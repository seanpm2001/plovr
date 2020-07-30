/**
 * @license
 * Copyright The Closure Library Authors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * @fileoverview Tests for goog.ui.MockActivityMonitorTest.
 */

/** @suppress {extraProvide} */
goog.module('goog.ui.MockActivityMonitorTest');
goog.setTestOnly();

const ActivityMonitor = goog.require('goog.ui.ActivityMonitor');
const MockActivityMonitor = goog.require('goog.ui.MockActivityMonitor');
const events = goog.require('goog.events');
const functions = goog.require('goog.functions');
const recordFunction = goog.require('goog.testing.recordFunction');
const testSuite = goog.require('goog.testing.testSuite');

const googNow = goog.now;
let monitor;
let recordedFunction;
let replacer;

testSuite({
  setUp() {
    monitor = new MockActivityMonitor();
    recordedFunction = recordFunction();

    events.listen(monitor, ActivityMonitor.Event.ACTIVITY, recordedFunction);
  },

  tearDown() {
    goog.dispose(monitor);
    goog.now = googNow;
  },

  testEventFireSameTime() {
    goog.now = functions.constant(1000);

    monitor.simulateEvent();
    assertEquals(1, recordedFunction.getCallCount());

    monitor.simulateEvent();
    assertEquals(2, recordedFunction.getCallCount());
  },

  testEventFireDifferingTime() {
    goog.now = functions.constant(1000);
    monitor.simulateEvent();
    assertEquals(1, recordedFunction.getCallCount());

    goog.now = functions.constant(1001);
    monitor.simulateEvent();
    assertEquals(2, recordedFunction.getCallCount());
  },

  testDispatchEventReturnValue() {
    assertTrue(monitor.dispatchEvent(ActivityMonitor.Event.ACTIVITY));
    assertEquals(1, recordedFunction.getCallCount());
  },

  testDispatchEventPreventDefault() {
    // Undo the listen call in setUp.
    events.unlisten(monitor, ActivityMonitor.Event.ACTIVITY, recordedFunction);

    // Listen with a function that cancels the event.
    events.listen(monitor, ActivityMonitor.Event.ACTIVITY, (e) => {
      e.preventDefault();
    });

    assertFalse(monitor.dispatchEvent(ActivityMonitor.Event.ACTIVITY));
  },
});
