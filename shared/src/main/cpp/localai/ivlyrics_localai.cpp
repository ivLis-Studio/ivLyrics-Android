// JNI bridge between LocalAiNative.java and llama.cpp b11364 (46ca246).
//
// The Java side owns scheduling: every call below runs on the provider's single worker
// thread, except nativeAbort, which only flips an atomic flag. Text crosses the boundary as
// UTF-8 byte arrays because JNI's modified UTF-8 cannot carry every lyric character.

#include <jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <new>
#include <stdexcept>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#endif

namespace {

constexpr int kStatusEndOfGeneration = 0;
constexpr int kStatusTokenLimit = 1;
constexpr int kPenaltyLastN = 64;

struct Engine {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    const llama_vocab * vocab = nullptr;
    std::string chat_template;
    std::string prompt_prefix;
    int batch_size = 512;
    std::atomic<bool> abort{false};

    ~Engine() {
        if (context != nullptr) llama_free(context);
        if (model != nullptr) llama_model_free(model);
    }
};

struct Cancelled : std::runtime_error {
    Cancelled() : std::runtime_error("cancelled") {}
};

struct SamplerDeleter {
    void operator()(llama_sampler * sampler) const { llama_sampler_free(sampler); }
};

void log_callback(ggml_log_level level, const char * text, void *) {
    if (level < GGML_LOG_LEVEL_ERROR || text == nullptr) return;
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_ERROR, "ivLyricsLocalAi", "%s", text);
#else
    std::fputs(text, stderr);
#endif
}

bool abort_requested(void * data) {
    return static_cast<Engine *>(data)->abort.load(std::memory_order_relaxed);
}

void throw_java(JNIEnv * env, const char * class_name, const std::string & message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass(class_name);
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

std::string read_bytes(JNIEnv * env, jbyteArray bytes) {
    if (bytes == nullptr) return {};
    const jsize length = env->GetArrayLength(bytes);
    std::string value(static_cast<size_t>(length), '\0');
    if (length > 0) env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(&value[0]));
    return value;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & value) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(value.size()));
    if (bytes != nullptr && !value.empty()) {
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()),
                reinterpret_cast<const jbyte *>(value.data()));
    }
    return bytes;
}

bool contains(const std::string & text, const char * needle) {
    return text.find(needle) != std::string::npos;
}

// Uses the GGUF's own chat template. b11364 classifies Hy-MT2's template as Hunyuan-VL because it also
// contains the BOS marker; that formatter would put the text before <｜hy_User｜> and drop the assistant
// turn. The template is the Hunyuan-dense one with an explicit BOS, so the built-in dense formatter plus
// that marker reproduces it exactly: <｜hy_begin▁of▁sentence｜><｜hy_User｜>{text}<｜hy_Assistant｜>.
void select_chat_template(Engine & engine) {
    const char * gguf_template = llama_model_chat_template(engine.model, nullptr);
    if (gguf_template == nullptr || *gguf_template == '\0') {
        throw std::runtime_error("The model has no chat template.");
    }
    const std::string source(gguf_template);
    if (contains(source, "<｜hy_User｜>") && contains(source, "<｜hy_Assistant｜>")
            && contains(source, "<｜hy_place▁holder▁no▁3｜>")) {
        engine.chat_template = "hunyuan-dense";
        engine.prompt_prefix = contains(source, "<｜hy_begin▁of▁sentence｜>") ? "<｜hy_begin▁of▁sentence｜>" : "";
    } else {
        engine.chat_template = source;
        engine.prompt_prefix.clear();
    }
}

std::string format_prompt(const Engine & engine, const std::string & user_text) {
    const llama_chat_message message{"user", user_text.c_str()};
    std::vector<char> buffer(user_text.size() * 2 + 512);
    int32_t length = llama_chat_apply_template(engine.chat_template.c_str(), &message, 1, true,
            buffer.data(), static_cast<int32_t>(buffer.size()));
    if (length > static_cast<int32_t>(buffer.size())) {
        buffer.resize(static_cast<size_t>(length));
        length = llama_chat_apply_template(engine.chat_template.c_str(), &message, 1, true,
                buffer.data(), static_cast<int32_t>(buffer.size()));
    }
    if (length < 0) throw std::runtime_error("The model's chat template is not supported.");
    return engine.prompt_prefix + std::string(buffer.data(), static_cast<size_t>(length));
}

std::vector<llama_token> tokenize(const Engine & engine, const std::string & text) {
    const int32_t needed = -llama_tokenize(engine.vocab, text.c_str(), static_cast<int32_t>(text.size()),
            nullptr, 0, true, true);
    if (needed <= 0) throw std::runtime_error("The prompt could not be tokenized.");
    std::vector<llama_token> tokens(static_cast<size_t>(needed));
    const int32_t count = llama_tokenize(engine.vocab, text.c_str(), static_cast<int32_t>(text.size()),
            tokens.data(), needed, true, true);
    if (count < 0) throw std::runtime_error("The prompt could not be tokenized.");
    tokens.resize(static_cast<size_t>(count));
    return tokens;
}

void decode(Engine & engine, llama_token * tokens, int32_t count) {
    const int32_t result = llama_decode(engine.context, llama_batch_get_one(tokens, count));
    if (result == 2 || engine.abort.load(std::memory_order_relaxed)) throw Cancelled();
    if (result != 0) throw std::runtime_error("llama_decode failed (" + std::to_string(result) + ").");
}

void append_piece(const Engine & engine, llama_token token, std::string & output) {
    char piece[256];
    const int32_t length = llama_token_to_piece(engine.vocab, token, piece, sizeof(piece), 0, false);
    if (length >= 0) {
        output.append(piece, static_cast<size_t>(length));
        return;
    }
    std::vector<char> larger(static_cast<size_t>(-length));
    const int32_t written = llama_token_to_piece(engine.vocab, token, larger.data(),
            static_cast<int32_t>(larger.size()), 0, false);
    if (written > 0) output.append(larger.data(), static_cast<size_t>(written));
}

std::string complete(Engine & engine, const std::string & user_text, int max_tokens, float temperature,
        float top_p, int top_k, float repeat_penalty, int & status, jlong & tokens, jlong & decode_ns) {
    std::vector<llama_token> prompt = tokenize(engine, format_prompt(engine, user_text));
    const int context_size = static_cast<int>(llama_n_ctx(engine.context));
    const int budget = std::min(max_tokens, context_size - static_cast<int>(prompt.size()));
    if (budget <= 0) throw std::runtime_error("The text is too long for the local model.");

    llama_memory_clear(llama_get_memory(engine.context), true);
    for (size_t offset = 0; offset < prompt.size(); offset += static_cast<size_t>(engine.batch_size)) {
        const size_t count = std::min(prompt.size() - offset, static_cast<size_t>(engine.batch_size));
        decode(engine, prompt.data() + offset, static_cast<int32_t>(count));
    }

    std::unique_ptr<llama_sampler, SamplerDeleter> sampler(
            llama_sampler_chain_init(llama_sampler_chain_default_params()));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_penalties(
            llama_vocab_n_tokens(engine.vocab), kPenaltyLastN, repeat_penalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    status = kStatusTokenLimit;
    tokens = 0;
    // Model loading, tokenization and prompt prefill have finished before generation is timed.
    const auto decode_started = std::chrono::steady_clock::now();
    for (int generated = 0; generated < budget; ++generated) {
        if (engine.abort.load(std::memory_order_relaxed)) throw Cancelled();
        llama_token token = llama_sampler_sample(sampler.get(), engine.context, -1);
        if (llama_vocab_is_eog(engine.vocab, token)) {
            status = kStatusEndOfGeneration;
            break;
        }
        append_piece(engine, token, output);
        decode(engine, &token, 1);
        ++tokens;
    }
    if (engine.abort.load(std::memory_order_relaxed)) throw Cancelled();
    decode_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now() - decode_started).count();
    return output;
}

Engine * engine_from(jlong handle) {
    return reinterpret_cast<Engine *>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_kr_ivlis_ivlyricsandroid_LocalAiNative_nativeInit(JNIEnv * env, jclass, jbyteArray backend_directory) {
    try {
        llama_log_set(log_callback, nullptr);
        const std::string directory = read_bytes(env, backend_directory);
        // Android ships ggml's CPU variants as separate libraries; ggml loads the best one this CPU supports.
        if (!directory.empty()) ggml_backend_load_all_from_path(directory.c_str());
        llama_backend_init();
        if (ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU) == nullptr) {
            throw std::runtime_error("No CPU backend could be loaded.");
        }
        return to_bytes(env, llama_print_system_info());
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/io/IOException", "Out of memory");
    } catch (const std::exception & error) {
        throw_java(env, "java/io/IOException", error.what());
    } catch (...) {
        throw_java(env, "java/io/IOException", "Native initialization failed.");
    }
    return nullptr;
}

JNIEXPORT jlong JNICALL
Java_kr_ivlis_ivlyricsandroid_LocalAiNative_nativeLoad(JNIEnv * env, jclass, jbyteArray model_path,
        jint context_size, jint batch_size, jint threads) {
    try {
        auto engine = std::make_unique<Engine>();
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
        model_params.progress_callback = [](float, void *) { return true; };
        const std::string path = read_bytes(env, model_path);
        engine->model = llama_model_load_from_file(path.c_str(), model_params);
        if (engine->model == nullptr) throw std::runtime_error("The model file could not be loaded.");
        engine->vocab = llama_model_get_vocab(engine->model);
        select_chat_template(*engine);

        llama_context_params context_params = llama_context_default_params();
        context_params.n_ctx = static_cast<uint32_t>(context_size);
        context_params.n_batch = static_cast<uint32_t>(batch_size);
        context_params.n_ubatch = static_cast<uint32_t>(batch_size);
        context_params.n_seq_max = 1;
        context_params.n_threads = threads;
        context_params.n_threads_batch = threads;
        context_params.offload_kqv = false;
        context_params.op_offload = false;
        context_params.no_perf = true;
        context_params.abort_callback = abort_requested;
        context_params.abort_callback_data = engine.get();
        engine->context = llama_init_from_model(engine->model, context_params);
        if (engine->context == nullptr) throw std::runtime_error("The model context could not be created.");
        engine->batch_size = batch_size;
        format_prompt(*engine, "probe");
        return static_cast<jlong>(reinterpret_cast<intptr_t>(engine.release()));
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/io/IOException", "Out of memory");
    } catch (const std::exception & error) {
        throw_java(env, "java/io/IOException", error.what());
    } catch (...) {
        throw_java(env, "java/io/IOException", "The model could not be loaded.");
    }
    return 0;
}

// Returns the UTF-8 reply followed by one status byte: 0 = end of generation, 1 = token limit.
JNIEXPORT jbyteArray JNICALL
Java_kr_ivlis_ivlyricsandroid_LocalAiNative_nativeComplete(JNIEnv * env, jclass, jlong handle,
        jbyteArray prompt, jint max_tokens, jfloat temperature, jfloat top_p, jint top_k, jfloat repeat_penalty,
        jlongArray statistics) {
    Engine * engine = engine_from(handle);
    if (engine == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "The model is not loaded.");
        return nullptr;
    }
    try {
        int status = kStatusTokenLimit;
        jlong measured[2] = {0, 0};
        std::string reply = complete(*engine, read_bytes(env, prompt), max_tokens, temperature, top_p, top_k,
                repeat_penalty, status, measured[0], measured[1]);
        if (statistics != nullptr && env->GetArrayLength(statistics) >= 2) {
            env->SetLongArrayRegion(statistics, 0, 2, measured);
        }
        reply.push_back(static_cast<char>(status));
        return to_bytes(env, reply);
    } catch (const Cancelled &) {
        throw_java(env, "java/util/concurrent/CancellationException", "Local AI request was cancelled.");
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/io/IOException", "Out of memory");
    } catch (const std::exception & error) {
        throw_java(env, "java/io/IOException", error.what());
    } catch (...) {
        throw_java(env, "java/io/IOException", "Local AI generation failed.");
    }
    return nullptr;
}

JNIEXPORT void JNICALL
Java_kr_ivlis_ivlyricsandroid_LocalAiNative_nativeAbort(JNIEnv *, jclass, jlong handle, jboolean abort) {
    Engine * engine = engine_from(handle);
    if (engine != nullptr) engine->abort.store(abort == JNI_TRUE, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_kr_ivlis_ivlyricsandroid_LocalAiNative_nativeFree(JNIEnv *, jclass, jlong handle) {
    delete engine_from(handle);
}

}  // extern "C"
