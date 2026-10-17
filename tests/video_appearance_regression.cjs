const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const properties = {};
const events = {};
let playerEvents, unavailable = 0, ready = 0, playCalls = 0;
const player = {
  mute() {}, playVideo() { playCalls++; }, getPlayerState() { return 1; },
  getCurrentTime() { return 1; }, getDuration() { return 100; }, getPlaybackRate() { return 1; }
};
const context = {
  innerWidth: 1920, innerHeight: 1080, console, Date,
  document: {documentElement: {style: {setProperty(key, value) {properties[key] = value;}}}},
  addEventListener(key, callback) {events[key] = callback;},
  setInterval() {}, setTimeout() {}, navigator: {},
  AndroidYouTubeBackground: {
    onReady(videoId, generation) {assert.equal(videoId, 'fixture'); assert.equal(generation, 7); ready++;},
    onUnavailable(videoId, generation) {assert.equal(videoId, 'fixture'); assert.equal(generation, 7); unavailable++;}
  },
  YT: {Player: function (_, options) {playerEvents = options.events; return player;}}
};
context.window = context;
vm.createContext(context);
vm.runInContext(fs.readFileSync(process.argv[2], 'utf8'), context);
assert.equal(Number(properties['--video-stretch']), 1);
context.innerWidth = 1000; context.innerHeight = 1000;
events.resize();
assert.equal(Number(properties['--video-stretch']), 16 / 9);
context.onYouTubeIframeAPIReady();
playerEvents.onReady({target: player});
assert.equal(ready, 1);
assert.equal(playCalls, 1);
playerEvents.onError({data: 150});
assert.equal(unavailable, 1, 'an unplayable video reveals the configured native fallback');
assert.equal(playCalls, 1, 'a playback error does not trigger an endless retry');
console.log('Video appearance JS: resize, ready and unavailable handoff passed');
