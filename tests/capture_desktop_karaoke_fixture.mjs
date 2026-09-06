#!/usr/bin/env node
// Capture numerical fixtures from the actual desktop renderer, not a reimplementation.
// Usage: node tests/capture_desktop_karaoke_fixture.mjs /path/to/ivLyrics/Pages.js > fixture.json
import fs from 'node:fs';
import crypto from 'node:crypto';
import vm from 'node:vm';

const source = fs.readFileSync(process.argv[2], 'utf8');
const section = (start, end) => {
  const begin = source.indexOf(start);
  const finish = source.indexOf(end, begin);
  if (begin < 0 || finish < 0) throw new Error(`Missing desktop boundary: ${start}`);
  return source.slice(begin, finish);
};
const declarations = [
  section('const KARAOKE_COMBINING_MARK_REGEX', 'const getKaraokeSyllableCharCount'),
  section('const buildKaraokeTimedChars =', 'const applyKaraokeWhitespaceCompensation'),
  section('const KARAOKE_FILL_STEPS =', 'const getKaraokeCharFill ='),
  section('const getKaraokeBounceValues =', 'const getKaraokeWordBounceValues ='),
].join('\n');
const cases = [
  ['fast Korean', [['가', 0, 75], ['나', 75, 155], ['다', 155, 235], ['라', 235, 310], ['마', 310, 385]]],
  ['medium syllables', [['하', 1000, 1260], ['늘', 1260, 1510], ['로', 1510, 1900]]],
  ['held syllable and gap', [['아', 1000, 2800], [' ', 2800, 2850], ['라', 4000, 4450]]],
  ['long word and trailing space', [['beautiful ', 1000, 3400], ['day', 3500, 4100]]],
  ['one phrase source', [['several words together', 1000, 5200], [' next', 5600, 6300]]],
  ['large compact source', [['abcdefghijklmnopqrstuvwxyzabcde', 0, 6200], ['끝', 6300, 6600]]],
  ['repeated onset', [['한', 1000, 1200], ['국', 1000, 1400], ['어', 1450, 1700], ['다', 1700, 2100]]],
  ['separate combining mark source', [['e', 1000, 1120], ['\u0301', 1120, 1120], ['lan ', 1120, 1900]]],
  ['emoji source', [['👨‍👩‍👧‍👦 ', 1000, 1800], ['끝', 1900, 2200]]],
];
const sandbox = {
  window: {Utils: {getDetectedLanguage: () => 'auto'}},
  CONFIG: {visual: {'karaoke-bounce': true}},
  prefersReducedLyricsMotion: () => false,
  getTimedSyllablesFromLine: line => line.syllables,
  console,
};
vm.createContext(sandbox);
vm.runInContext(`${declarations}\nthis.motion = {buildKaraokeTimedChars, getKaraokeMotionProfile, getKaraokeBounceValues};`, sandbox);
const records = [];
for (const [name, units] of cases) {
  const syllables = units.map(([text, startTime, endTime]) => ({text, startTime, endTime}));
  const chars = sandbox.motion.buildKaraokeTimedChars({syllables});
  // Match the native renderer's integer fill clock without changing source-unit cadence.
  chars.forEach(char => {
    char.karaokeFillStartTime = Math.round(char.startTime);
    char.karaokeFillEndTime = Math.round(char.endTime);
  });
  const segments = [];
  let offset = 0;
  chars.forEach((char, index) => {
    const codePoints = [...char.char].length;
    if (/\S/u.test(char.char)) {
      const counts = [1, Math.min(3, chars.length - index)];
      if (index === 0 || /\s/u.test(chars[index - 1].char)) {
        let wordLength = 1;
        while (index + wordLength < chars.length && /\S/u.test(chars[index + wordLength].char)) wordLength++;
        counts.push(wordLength);
      }
      for (const count of [...new Set(counts)]) {
        const selected = chars.slice(index, index + count);
        if (selected.some(value => /\s/u.test(value.char))) continue;
        const profile = sandbox.motion.getKaraokeMotionProfile(chars, index, count);
        if (!profile) continue;
        const fillStart = char.karaokeFillStartTime;
        const fillEnd = Math.max(...selected.map(value => value.karaokeFillEndTime));
        const times = [...new Set([
          Math.max(0, fillStart - 1), fillStart, Math.round(fillStart + profile.riseDuration / 2),
          Math.round(fillStart + profile.riseDuration), Math.round((fillStart + profile.endTime) / 2),
          Math.round(profile.endTime), Math.round(profile.endTime + profile.releaseDuration / 2),
          Math.ceil(profile.endTime + profile.releaseDuration),
        ])].sort((a, b) => a - b);
        segments.push({
          offset, length: selected.reduce((sum, value) => sum + [...value.char].length, 0), fillStart, fillEnd,
          profile: [profile.startTime, profile.endTime, profile.riseDuration, profile.releaseDuration,
            profile.amplitude, profile.scaleAmount],
          samples: times.map(time => {
            const motion = sandbox.motion.getKaraokeBounceValues(time, true, fillStart, fillEnd, 1, profile);
            return [time, motion.offsetY, motion.scale];
          }),
        });
      }
    }
    offset += codePoints;
  });
  records.push({name, units, segments});
}
process.stdout.write(JSON.stringify({
  reference: 'ivLyrics desktop 6.6.8 Pages.js, original font baseline 44 CSS px',
  sourceSha256: crypto.createHash('sha256').update(source).digest('hex'),
  declarationsSha256: crypto.createHash('sha256').update(declarations).digest('hex'),
  records,
}) + '\n');
