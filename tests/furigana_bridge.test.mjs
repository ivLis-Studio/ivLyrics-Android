import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { posix } from 'node:path';
import test from 'node:test';
import vm from 'node:vm';

const html = readFileSync(new URL('../shared/src/main/assets/furigana/bridge.html', import.meta.url), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const flush = async () => { for (let i = 0; i < 8; i++) await new Promise(resolve => setImmediate(resolve)); };

function load({ library = () => 'success', dictionary = () => 'success' } = {}) {
    const replies = [], errors = [], libraries = [], dictionaries = [], requests = [], timers = new Map();
    class XMLHttpRequest {
        open(...args) { requests.push({ xhr: this, args }); return 'opened'; }
    }
    let timerId = 0, tokenizations = 0, ready = 0;
    const tokenizer = { tokenize(text) {
        tokenizations++;
        return [{ surface_form: text, reading: text === '日本語' ? 'ニホンゴ' : 'トウキョウ' }];
    } };
    const kuromoji = { builder({ dicPath }) {
        dictionaries.push(dicPath);
        return { build(callback) {
            // Match Kuromoji 0.1.2's BrowserDictionaryLoader URL construction.
            new XMLHttpRequest().open('GET', posix.join(dicPath, 'base.dat.gz'), true);
            const outcome = dictionary(dicPath, dictionaries.length);
            if (outcome !== 'hang') queueMicrotask(() => callback(
                outcome === 'success' ? null : new Error('dictionary offline'), tokenizer));
        } };
    } };
    const window = {
        AndroidFurigana: {
            onLog() {}, onReady() { ready++; },
            onInitializationError(message) { errors.push(message); },
            onResult(id, raw) { replies.push({ id, ...JSON.parse(raw) }); },
        },
    };
    const context = vm.createContext({ window, XMLHttpRequest,
        document: {
            createElement: () => ({ remove() {} }),
            head: { appendChild(element) {
                libraries.push(element.src);
                const outcome = library(element.src, libraries.length);
                if (outcome === 'hang') return;
                queueMicrotask(() => {
                    if (outcome === 'success') { window.kuromoji = kuromoji; element.onload?.(); }
                    else element.onerror?.();
                });
            } },
        },
        setTimeout: (fn, delay) => { timers.set(++timerId, { fn, delay }); return timerId; },
        clearTimeout: id => timers.delete(id),
    });
    vm.runInContext(script, context);
    return {
        window, replies, errors, libraries, dictionaries, requests, timers, XMLHttpRequest,
        tokenizations: () => tokenizations, ready: () => ready,
        request: (id, lines = ['日本語']) => window.ivLyricsFurigana.request(id, JSON.stringify({ lines })),
        expire: async delay => {
            const entry = [...timers].find(([, timer]) => timer.delay === delay);
            assert.ok(entry, `missing ${delay}ms timeout`);
            timers.delete(entry[0]); entry[1].fn(); await flush();
        },
    };
}

test('concurrent requests share initialization and cache ruby conversion while retaining line order', async () => {
    const h = load();
    await Promise.all([h.request('first', ['日本語', '', 'かな']), h.request('second')]);
    assert.equal(h.libraries.length, 1);
    assert.equal(h.dictionaries.length, 1);
    assert.equal(h.ready(), 1);
    assert.deepEqual(h.replies.find(reply => reply.id === 'first').lines,
        ['<ruby>日本語<rt>にほんご</rt></ruby>', '', 'かな']);
    assert.equal(h.tokenizations(), 1);
    assert.equal(h.timers.size, 0);
});

test('failed primary library and dictionary requests fall back independently', async () => {
    const h = load({ library: (_, count) => count === 1 ? 'error' : 'success',
        dictionary: (_, count) => count === 1 ? 'error' : 'success' });
    await h.request('fallback');
    assert.equal(h.libraries.length, 2);
    assert.ok(h.libraries[1].startsWith('https://unpkg.com/'));
    assert.equal(h.dictionaries.length, 2);
    assert.ok(h.dictionaries[1].startsWith('https://unpkg.com/'));
    assert.equal(h.replies[0].ok, true);
    assert.match(h.replies[0].lines[0], /<rt>にほんご<\/rt>/);
    assert.deepEqual(h.errors, []);
});

test('hung CDN requests time out and allow the alternative source to finish', async () => {
    const h = load({ library: (_, count) => count === 1 ? 'hang' : 'success',
        dictionary: (_, count) => count === 1 ? 'hang' : 'success' });
    const request = h.request('timeout');
    await h.expire(6000);
    await h.expire(12000);
    await request;
    assert.equal(h.replies[0].ok, true);
    assert.equal(h.timers.size, 0);
});

test('failed library initialization reports an error and a later request can retry', async () => {
    let offline = true;
    const h = load({ library: () => offline ? 'error' : 'success' });
    await h.request('failed'); await flush();
    assert.equal(h.replies[0].ok, false);
    assert.equal(h.errors.length, 1);
    offline = false;
    await h.request('retried');
    assert.equal(h.replies[1].ok, true);
    assert.equal(h.libraries.length, 3);
    assert.equal(h.timers.size, 0);
});

test('a rejected dictionary initialization is retried instead of being cached forever', async () => {
    let offline = true;
    const h = load({ dictionary: () => offline ? 'error' : 'success' });
    await h.request('failed'); await flush();
    assert.equal(h.replies[0].ok, false);
    offline = false;
    await h.request('retried');
    assert.equal(h.replies[1].ok, true);
    assert.equal(h.libraries.length, 1);
    assert.equal(h.dictionaries.length, 3);
    assert.equal(h.timers.size, 0);
});

test('Kuromoji path.join dictionary URLs keep their CDN origin on an HTTPS document', async () => {
    const h = load({ dictionary: (_, count) => count === 1 ? 'error' : 'success' });
    await h.request('url-fallback');
    assert.deepEqual(h.requests.map(request => request.args[1]), [
        'https://cdn.jsdelivr.net/npm/kuromoji@0.1.2/dict/base.dat.gz',
        'https://unpkg.com/kuromoji@0.1.2/dict/base.dat.gz',
    ]);
    for (const request of h.requests) {
        const resolved = new URL(request.args[1], 'https://appassets.androidplatform.net/furigana/bridge.html');
        assert.notEqual(resolved.hostname, 'appassets.androidplatform.net');
    }
    const xhr = new h.XMLHttpRequest();
    for (const url of ['https://cdn.jsdelivr.net/npm/kuromoji@0.1.2/dict/check.dat.gz',
        'https:/unrelated.example/dict/base.dat.gz', '/local/resource',
        'https:/unpkg.com/kuromoji@0.1.2/dictionary/base.dat.gz']) {
        assert.equal(xhr.open('POST', url, false, 'user', 'password'), 'opened');
        assert.equal(h.requests.at(-1).xhr, xhr);
        assert.deepEqual(h.requests.at(-1).args, ['POST', url, false, 'user', 'password']);
    }
});
