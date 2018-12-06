/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.wm.ActivityStack.ActivityState.DESTROYING;
import static com.android.server.wm.ActivityStack.ActivityState.PAUSED;
import static com.android.server.wm.ActivityStack.ActivityState.PAUSING;
import static com.android.server.wm.ActivityStack.ActivityState.RESUMED;
import static com.android.server.wm.ActivityStack.ActivityState.STOPPING;
import static com.android.server.wm.ActivityStack.REMOVE_TASK_MODE_DESTROYING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import android.content.pm.ActivityInfo;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the {@link ActivityStack} class.
 *
 * Build/Install/Run:
 *  atest WmTests:ActivityStackTests
 */
@SmallTest
@Presubmit
public class ActivityStackTests extends ActivityTestsBase {
    private ActivityDisplay mDefaultDisplay;
    private ActivityStack mStack;
    private TaskRecord mTask;

    @Before
    public void setUp() throws Exception {
        setupActivityTaskManagerService();
        mDefaultDisplay = mRootActivityContainer.getDefaultDisplay();
        mStack = spy(mDefaultDisplay.createStack(WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD,
                true /* onTop */));
        mTask = new TaskBuilder(mSupervisor).setStack(mStack).build();
    }

    @Test
    public void testEmptyTaskCleanupOnRemove() {
        assertNotNull(mTask.getTask());
        mStack.removeTask(mTask, "testEmptyTaskCleanupOnRemove", REMOVE_TASK_MODE_DESTROYING);
        assertNull(mTask.getTask());
    }

    @Test
    public void testOccupiedTaskCleanupOnRemove() {
        final ActivityRecord r = new ActivityBuilder(mService).setTask(mTask).build();
        assertNotNull(mTask.getTask());
        mStack.removeTask(mTask, "testOccupiedTaskCleanupOnRemove", REMOVE_TASK_MODE_DESTROYING);
        assertNotNull(mTask.getTask());
    }

    @Test
    public void testResumedActivity() {
        final ActivityRecord r = new ActivityBuilder(mService).setTask(mTask).build();
        assertNull(mStack.getResumedActivity());
        r.setState(RESUMED, "testResumedActivity");
        assertEquals(r, mStack.getResumedActivity());
        r.setState(PAUSING, "testResumedActivity");
        assertNull(mStack.getResumedActivity());
    }

    @Test
    public void testResumedActivityFromTaskReparenting() {
        final ActivityRecord r = new ActivityBuilder(mService).setTask(mTask).build();
        // Ensure moving task between two stacks updates resumed activity
        r.setState(RESUMED, "testResumedActivityFromTaskReparenting");
        assertEquals(r, mStack.getResumedActivity());

        final ActivityStack destStack = mRootActivityContainer.getDefaultDisplay().createStack(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        mTask.reparent(destStack, true /* toTop */, TaskRecord.REPARENT_KEEP_STACK_AT_FRONT,
                false /* animate */, true /* deferResume*/,
                "testResumedActivityFromTaskReparenting");

        assertNull(mStack.getResumedActivity());
        assertEquals(r, destStack.getResumedActivity());
    }

    @Test
    public void testResumedActivityFromActivityReparenting() {
        final ActivityRecord r = new ActivityBuilder(mService).setTask(mTask).build();
        // Ensure moving task between two stacks updates resumed activity
        r.setState(RESUMED, "testResumedActivityFromActivityReparenting");
        assertEquals(r, mStack.getResumedActivity());

        final ActivityStack destStack = mRootActivityContainer.getDefaultDisplay().createStack(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final TaskRecord destTask = new TaskBuilder(mSupervisor).setStack(destStack).build();

        mTask.removeActivity(r);
        destTask.addActivityToTop(r);

        assertNull(mStack.getResumedActivity());
        assertEquals(r, destStack.getResumedActivity());
    }

    @Test
    public void testPrimarySplitScreenRestoresWhenMovedToBack() {
        // Create primary splitscreen stack. This will create secondary stacks and places the
        // existing fullscreen stack on the bottom.
        final ActivityStack primarySplitScreen = mDefaultDisplay.createStack(
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        // Assert windowing mode.
        assertEquals(WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, primarySplitScreen.getWindowingMode());

        // Move primary to back.
        primarySplitScreen.moveToBack("testPrimarySplitScreenToFullscreenWhenMovedToBack",
                null /* task */);

        // Assert that stack is at the bottom.
        assertEquals(0, mDefaultDisplay.getIndexOf(primarySplitScreen));

        // Ensure no longer in splitscreen.
        assertEquals(WINDOWING_MODE_FULLSCREEN, primarySplitScreen.getWindowingMode());

        // Ensure that the override mode is restored to undefined
        assertEquals(WINDOWING_MODE_UNDEFINED,
                primarySplitScreen.getRequestedOverrideWindowingMode());
    }

    @Test
    public void testPrimarySplitScreenRestoresPreviousWhenMovedToBack() {
        // This time, start with a fullscreen activitystack
        final ActivityStack primarySplitScreen = mDefaultDisplay.createStack(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        primarySplitScreen.setWindowingMode(WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);

        // Assert windowing mode.
        assertEquals(WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, primarySplitScreen.getWindowingMode());

        // Move primary to back.
        primarySplitScreen.moveToBack("testPrimarySplitScreenToFullscreenWhenMovedToBack",
                null /* task */);

        // Assert that stack is at the bottom.
        assertEquals(0, mDefaultDisplay.getIndexOf(primarySplitScreen));

        // Ensure that the override mode is restored to what it was (fullscreen)
        assertEquals(WINDOWING_MODE_FULLSCREEN,
                primarySplitScreen.getRequestedOverrideWindowingMode());
    }

    @Test
    public void testStackInheritsDisplayWindowingMode() {
        final ActivityStack primarySplitScreen = mDefaultDisplay.createStack(
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        assertEquals(WINDOWING_MODE_FULLSCREEN, primarySplitScreen.getWindowingMode());
        assertEquals(WINDOWING_MODE_UNDEFINED,
                primarySplitScreen.getRequestedOverrideWindowingMode());

        mDefaultDisplay.setWindowingMode(WINDOWING_MODE_FREEFORM);
        assertEquals(WINDOWING_MODE_FREEFORM, primarySplitScreen.getWindowingMode());
        assertEquals(WINDOWING_MODE_UNDEFINED,
                primarySplitScreen.getRequestedOverrideWindowingMode());
    }

    @Test
    public void testStackOverridesDisplayWindowingMode() {
        final ActivityStack primarySplitScreen = mDefaultDisplay.createStack(
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        assertEquals(WINDOWING_MODE_FULLSCREEN, primarySplitScreen.getWindowingMode());
        assertEquals(WINDOWING_MODE_UNDEFINED,
                primarySplitScreen.getRequestedOverrideWindowingMode());

        primarySplitScreen.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        // setting windowing mode should still work even though resolved mode is already fullscreen
        assertEquals(WINDOWING_MODE_FULLSCREEN,
                primarySplitScreen.getRequestedOverrideWindowingMode());

        mDefaultDisplay.setWindowingMode(WINDOWING_MODE_FREEFORM);
        assertEquals(WINDOWING_MODE_FULLSCREEN, primarySplitScreen.getWindowingMode());
    }

    @Test
    public void testStopActivityWhenActivityDestroyed() {
        final ActivityRecord r = new ActivityBuilder(mService).setTask(mTask).build();
        r.info.flags |= ActivityInfo.FLAG_NO_HISTORY;
        mStack.moveToFront("testStopActivityWithDestroy");
        mStack.stopActivityLocked(r);
        // Mostly testing to make sure there is a crash in the call part, so if we get here we are
        // good-to-go!
    }

    @Test
    public void testFindTaskWithOverlay() {
        final ActivityRecord r = new ActivityBuilder(mService)
                .setCreateTask(true)
                .setStack(mStack)
                .setUid(0)
                .build();
        final TaskRecord task = r.getTaskRecord();
        // Overlay must be for a different user to prevent recognizing a matching top activity
        final ActivityRecord taskOverlay = new ActivityBuilder(mService).setTask(task)
                .setUid(UserHandle.PER_USER_RANGE * 2).build();
        taskOverlay.mTaskOverlay = true;

        final RootActivityContainer.FindTaskResult result =
                new RootActivityContainer.FindTaskResult();
        mStack.findTaskLocked(r, result);

        assertEquals(r, task.getTopActivity(false /* includeOverlays */));
        assertEquals(taskOverlay, task.getTopActivity(true /* includeOverlays */));
        assertNotNull(result.mRecord);
    }

    @Test
    public void testMoveStackToBackIncludingParent() {
        final ActivityDisplay display = addNewActivityDisplayAt(ActivityDisplay.POSITION_TOP);
        final ActivityStack stack1 = createStackForShouldBeVisibleTest(display,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final ActivityStack stack2 = createStackForShouldBeVisibleTest(display,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        // Do not move display to back because there is still another stack.
        stack2.moveToBack("testMoveStackToBackIncludingParent", stack2.topTask());
        verify(stack2.getWindowContainerController()).positionChildAtBottom(any(),
                eq(false) /* includingParents */);

        // Also move display to back because there is only one stack left.
        display.removeChild(stack1);
        stack2.moveToBack("testMoveStackToBackIncludingParent", stack2.topTask());
        verify(stack2.getWindowContainerController()).positionChildAtBottom(any(),
                eq(true) /* includingParents */);
    }

    @Test
    public void testShouldBeVisible_Fullscreen() {
        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);
        final ActivityStack pinnedStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        assertTrue(homeStack.shouldBeVisible(null /* starting */));
        assertTrue(pinnedStack.shouldBeVisible(null /* starting */));

        final TestActivityStack fullscreenStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        // Home stack shouldn't be visible behind an opaque fullscreen stack, but pinned stack
        // should be visible since it is always on-top.
        fullscreenStack.setIsTranslucent(false);
        assertFalse(homeStack.shouldBeVisible(null /* starting */));
        assertTrue(pinnedStack.shouldBeVisible(null /* starting */));
        assertTrue(fullscreenStack.shouldBeVisible(null /* starting */));

        // Home stack should be visible behind a translucent fullscreen stack.
        fullscreenStack.setIsTranslucent(true);
        assertTrue(homeStack.shouldBeVisible(null /* starting */));
        assertTrue(pinnedStack.shouldBeVisible(null /* starting */));
    }

    @Test
    public void testShouldBeVisible_SplitScreen() {
        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);
        // Home stack should always be fullscreen for this test.
        homeStack.setSupportsSplitScreen(false);
        final TestActivityStack splitScreenPrimary =
                createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final TestActivityStack splitScreenSecondary =
                createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        // Home stack shouldn't be visible if both halves of split-screen are opaque.
        splitScreenPrimary.setIsTranslucent(false);
        splitScreenSecondary.setIsTranslucent(false);
        assertFalse(homeStack.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary.shouldBeVisible(null /* starting */));

        // Home stack should be visible if one of the halves of split-screen is translucent.
        splitScreenPrimary.setIsTranslucent(true);
        assertTrue(homeStack.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary.shouldBeVisible(null /* starting */));

        final TestActivityStack splitScreenSecondary2 =
                createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        // First split-screen secondary shouldn't be visible behind another opaque split-split
        // secondary.
        splitScreenSecondary2.setIsTranslucent(false);
        assertFalse(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary2.shouldBeVisible(null /* starting */));

        // First split-screen secondary should be visible behind another translucent split-screen
        // secondary.
        splitScreenSecondary2.setIsTranslucent(true);
        assertTrue(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary2.shouldBeVisible(null /* starting */));

        final TestActivityStack assistantStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_ASSISTANT, true /* onTop */);

        // Split-screen stacks shouldn't be visible behind an opaque fullscreen stack.
        assistantStack.setIsTranslucent(false);
        assertTrue(assistantStack.shouldBeVisible(null /* starting */));
        assertFalse(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertFalse(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertFalse(splitScreenSecondary2.shouldBeVisible(null /* starting */));

        // Split-screen stacks should be visible behind a translucent fullscreen stack.
        assistantStack.setIsTranslucent(true);
        assertTrue(assistantStack.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary2.shouldBeVisible(null /* starting */));

        // Assistant stack shouldn't be visible behind translucent split-screen stack
        assistantStack.setIsTranslucent(false);
        splitScreenPrimary.setIsTranslucent(true);
        splitScreenSecondary2.setIsTranslucent(true);
        splitScreenSecondary2.moveToFront("testShouldBeVisible_SplitScreen");
        splitScreenPrimary.moveToFront("testShouldBeVisible_SplitScreen");
        assertFalse(assistantStack.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary2.shouldBeVisible(null /* starting */));
    }

    @Test
    public void testShouldBeVisible_Finishing() {
        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);
        final TestActivityStack translucentStack = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        translucentStack.setIsTranslucent(true);

        assertTrue(homeStack.shouldBeVisible(null /* starting */));
        assertTrue(translucentStack.shouldBeVisible(null /* starting */));

        final ActivityRecord topRunningHomeActivity = homeStack.topRunningActivityLocked();
        topRunningHomeActivity.finishing = true;
        final ActivityRecord topRunningTranslucentActivity =
                translucentStack.topRunningActivityLocked();
        topRunningTranslucentActivity.finishing = true;

        // Home stack should be visible even there are no running activities.
        assertTrue(homeStack.shouldBeVisible(null /* starting */));
        // Home should be visible if we are starting an activity within it.
        assertTrue(homeStack.shouldBeVisible(topRunningHomeActivity /* starting */));
        // The translucent stack shouldn't be visible since its activity marked as finishing.
        assertFalse(translucentStack.shouldBeVisible(null /* starting */));
    }

    @Test
    public void testMoveHomeStackBehindBottomMostVisibleStack_NoMoveHomeBehindFullscreen() {
        mDefaultDisplay.removeChild(mStack);

        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);
        final TestActivityStack fullscreenStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        homeStack.setIsTranslucent(false);
        fullscreenStack.setIsTranslucent(false);

        // Ensure that we don't move the home stack if it is already behind the top fullscreen stack
        int homeStackIndex = mDefaultDisplay.getIndexOf(homeStack);
        assertEquals(fullscreenStack, mDefaultDisplay.getStackAbove(homeStack));
        mDefaultDisplay.moveStackBehindBottomMostVisibleStack(homeStack);
        assertEquals(homeStackIndex, mDefaultDisplay.getIndexOf(homeStack));
    }

    @Test
    public void testMoveHomeStackBehindBottomMostVisibleStack_NoMoveHomeBehindTranslucent() {
        mDefaultDisplay.removeChild(mStack);

        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);
        final TestActivityStack fullscreenStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        homeStack.setIsTranslucent(false);
        fullscreenStack.setIsTranslucent(true);

        // Ensure that we don't move the home stack if it is already behind the top fullscreen stack
        int homeStackIndex = mDefaultDisplay.getIndexOf(homeStack);
        assertEquals(fullscreenStack, mDefaultDisplay.getStackAbove(homeStack));
        mDefaultDisplay.moveStackBehindBottomMostVisibleStack(homeStack);
        assertEquals(homeStackIndex, mDefaultDisplay.getIndexOf(homeStack));
    }

    @Test
    public void testMoveHomeStackBehindBottomMostVisibleStack_NoMoveHomeOnTop() {
        mDefaultDisplay.removeChild(mStack);

        final TestActivityStack fullscreenStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);

        homeStack.setIsTranslucent(false);
        fullscreenStack.setIsTranslucent(false);

        // Ensure we don't move the home stack if it is already on top
        int homeStackIndex = mDefaultDisplay.getIndexOf(homeStack);
        assertNull(mDefaultDisplay.getStackAbove(homeStack));
        mDefaultDisplay.moveStackBehindBottomMostVisibleStack(homeStack);
        assertEquals(homeStackIndex, mDefaultDisplay.getIndexOf(homeStack));
    }

    @Test
    public void testMoveHomeStackBehindBottomMostVisibleStack_MoveHomeBehindFullscreen() {
        mDefaultDisplay.removeChild(mStack);

        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);
        final TestActivityStack fullscreenStack1 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack fullscreenStack2 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final ActivityStack pinnedStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        homeStack.setIsTranslucent(false);
        fullscreenStack1.setIsTranslucent(false);
        fullscreenStack2.setIsTranslucent(false);

        // Ensure that we move the home stack behind the bottom most fullscreen stack, ignoring the
        // pinned stack
        assertEquals(fullscreenStack1, mDefaultDisplay.getStackAbove(homeStack));
        mDefaultDisplay.moveStackBehindBottomMostVisibleStack(homeStack);
        assertEquals(fullscreenStack2, mDefaultDisplay.getStackAbove(homeStack));
    }

    @Test
    public void
            testMoveHomeStackBehindBottomMostVisibleStack_MoveHomeBehindFullscreenAndTranslucent() {
        mDefaultDisplay.removeChild(mStack);

        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);
        final TestActivityStack fullscreenStack1 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack fullscreenStack2 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);

        homeStack.setIsTranslucent(false);
        fullscreenStack1.setIsTranslucent(false);
        fullscreenStack2.setIsTranslucent(true);

        // Ensure that we move the home stack behind the bottom most non-translucent fullscreen
        // stack
        assertEquals(fullscreenStack1, mDefaultDisplay.getStackAbove(homeStack));
        mDefaultDisplay.moveStackBehindBottomMostVisibleStack(homeStack);
        assertEquals(fullscreenStack1, mDefaultDisplay.getStackAbove(homeStack));
    }

    @Test
    public void testMoveHomeStackBehindStack_BehindHomeStack() {
        mDefaultDisplay.removeChild(mStack);

        final TestActivityStack fullscreenStack1 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack fullscreenStack2 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);

        homeStack.setIsTranslucent(false);
        fullscreenStack1.setIsTranslucent(false);
        fullscreenStack2.setIsTranslucent(false);

        // Ensure we don't move the home stack behind itself
        int homeStackIndex = mDefaultDisplay.getIndexOf(homeStack);
        mDefaultDisplay.moveStackBehindStack(homeStack, homeStack);
        assertEquals(homeStackIndex, mDefaultDisplay.getIndexOf(homeStack));
    }

    @Test
    public void testMoveHomeStackBehindStack() {
        mDefaultDisplay.removeChild(mStack);

        final TestActivityStack fullscreenStack1 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack fullscreenStack2 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack fullscreenStack3 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack fullscreenStack4 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);

        mDefaultDisplay.moveStackBehindStack(homeStack, fullscreenStack1);
        assertEquals(fullscreenStack1, mDefaultDisplay.getStackAbove(homeStack));
        mDefaultDisplay.moveStackBehindStack(homeStack, fullscreenStack2);
        assertEquals(fullscreenStack2, mDefaultDisplay.getStackAbove(homeStack));
        mDefaultDisplay.moveStackBehindStack(homeStack, fullscreenStack4);
        assertEquals(fullscreenStack4, mDefaultDisplay.getStackAbove(homeStack));
        mDefaultDisplay.moveStackBehindStack(homeStack, fullscreenStack2);
        assertEquals(fullscreenStack2, mDefaultDisplay.getStackAbove(homeStack));
    }

    @Test
    public void testSetAlwaysOnTop() {
        final TestActivityStack homeStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */);
        final ActivityStack pinnedStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        assertEquals(pinnedStack, mDefaultDisplay.getStackAbove(homeStack));

        final TestActivityStack alwaysOnTopStack = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        alwaysOnTopStack.setAlwaysOnTop(true);
        assertTrue(alwaysOnTopStack.isAlwaysOnTop());
        // Ensure (non-pinned) always on top stack is put below pinned stack.
        assertEquals(pinnedStack, mDefaultDisplay.getStackAbove(alwaysOnTopStack));

        final TestActivityStack nonAlwaysOnTopStack = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        // Ensure non always on top stack is put below always on top stacks.
        assertEquals(alwaysOnTopStack, mDefaultDisplay.getStackAbove(nonAlwaysOnTopStack));

        final TestActivityStack alwaysOnTopStack2 = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        alwaysOnTopStack2.setAlwaysOnTop(true);
        assertTrue(alwaysOnTopStack2.isAlwaysOnTop());
        // Ensure newly created always on top stack is placed above other all always on top stacks.
        assertEquals(pinnedStack, mDefaultDisplay.getStackAbove(alwaysOnTopStack2));

        alwaysOnTopStack2.setAlwaysOnTop(false);
        // Ensure, when always on top is turned off for a stack, the stack is put just below all
        // other always on top stacks.
        assertEquals(alwaysOnTopStack, mDefaultDisplay.getStackAbove(alwaysOnTopStack2));
        alwaysOnTopStack2.setAlwaysOnTop(true);

        // Ensure always on top state changes properly when windowing mode changes.
        alwaysOnTopStack2.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        assertFalse(alwaysOnTopStack2.isAlwaysOnTop());
        assertEquals(alwaysOnTopStack, mDefaultDisplay.getStackAbove(alwaysOnTopStack2));
        alwaysOnTopStack2.setWindowingMode(WINDOWING_MODE_FREEFORM);
        assertTrue(alwaysOnTopStack2.isAlwaysOnTop());
        assertEquals(pinnedStack, mDefaultDisplay.getStackAbove(alwaysOnTopStack2));
    }

    @Test
    public void testSplitScreenMoveToFront() {
        final TestActivityStack splitScreenPrimary = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack splitScreenSecondary = createStackForShouldBeVisibleTest(
                mDefaultDisplay, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);
        final TestActivityStack assistantStack = createStackForShouldBeVisibleTest(mDefaultDisplay,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_ASSISTANT, true /* onTop */);

        splitScreenPrimary.setIsTranslucent(false);
        splitScreenSecondary.setIsTranslucent(false);
        assistantStack.setIsTranslucent(false);

        assertFalse(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertFalse(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertTrue(assistantStack.shouldBeVisible(null /* starting */));

        splitScreenSecondary.moveToFront("testSplitScreenMoveToFront");

        assertTrue(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertFalse(assistantStack.shouldBeVisible(null /* starting */));
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    private <T extends ActivityStack> T createStackForShouldBeVisibleTest(
            ActivityDisplay display, int windowingMode, int activityType, boolean onTop) {
        final T stack;
        if (activityType == ACTIVITY_TYPE_HOME) {
            // Home stack and activity are created in ActivityTestsBase#setupActivityManagerService
            stack = mDefaultDisplay.getStack(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME);
            if (onTop) {
                mDefaultDisplay.positionChildAtTop(stack, false /* includingParents */);
            } else {
                mDefaultDisplay.positionChildAtBottom(stack);
            }
        } else {
            stack = display.createStack(windowingMode, activityType, onTop);
            final ActivityRecord r = new ActivityBuilder(mService).setUid(0).setStack(stack)
                    .setCreateTask(true).build();
        }
        return stack;
    }

    @Test
    public void testFinishDisabledPackageActivities() {
        final ActivityRecord firstActivity = new ActivityBuilder(mService).setTask(mTask).build();
        final ActivityRecord secondActivity = new ActivityBuilder(mService).setTask(mTask).build();

        // Making the second activity a task overlay without an app means it will be removed from
        // the task's activities as well once first activity is removed.
        secondActivity.mTaskOverlay = true;
        secondActivity.app = null;

        assertEquals(2, mTask.mActivities.size());

        mStack.finishDisabledPackageActivitiesLocked(firstActivity.packageName, null,
                true /* doit */, true /* evenPersistent */, UserHandle.USER_ALL);

        assertThat(mTask.mActivities).isEmpty();
        assertThat(mStack.getAllTasks()).isEmpty();
    }

    @Test
    public void testHandleAppDied() {
        final ActivityRecord firstActivity = new ActivityBuilder(mService).setTask(mTask).build();
        final ActivityRecord secondActivity = new ActivityBuilder(mService).setTask(mTask).build();

        // Making the first activity a task overlay means it will be removed from the task's
        // activities as well once second activity is removed as handleAppDied processes the
        // activity list in reverse.
        firstActivity.mTaskOverlay = true;
        firstActivity.app = null;

        // second activity will be immediately removed as it has no state.
        secondActivity.haveState = false;

        assertEquals(2, mTask.mActivities.size());

        mStack.handleAppDiedLocked(secondActivity.app);

        assertThat(mTask.mActivities).isEmpty();
        assertThat(mStack.getAllTasks()).isEmpty();
    }

    @Test
    public void testFinishCurrentActivity() {
        // Create 2 activities on a new display.
        final ActivityDisplay display = addNewActivityDisplayAt(ActivityDisplay.POSITION_TOP);
        final ActivityStack stack1 = createStackForShouldBeVisibleTest(display,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final ActivityStack stack2 = createStackForShouldBeVisibleTest(display,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        // There is still an activity1 in stack1 so the activity2 should be added to finishing list
        // that will be destroyed until idle.
        stack2.getTopActivity().visible = true;
        final ActivityRecord activity2 = finishCurrentActivity(stack2);
        assertEquals(STOPPING, activity2.getState());
        assertThat(mSupervisor.mStoppingActivities).contains(activity2);

        // The display becomes empty. Since there is no next activity to be idle, the activity
        // should be destroyed immediately with updating configuration to restore original state.
        final ActivityRecord activity1 = finishCurrentActivity(stack1);
        assertEquals(DESTROYING, activity1.getState());
        verify(mRootActivityContainer).ensureVisibilityAndConfig(eq(null) /* starting */,
                eq(display.mDisplayId), anyBoolean(), anyBoolean());
    }

    private ActivityRecord finishCurrentActivity(ActivityStack stack) {
        final ActivityRecord activity = stack.topRunningActivityLocked();
        assertNotNull(activity);
        activity.setState(PAUSED, "finishCurrentActivity");
        activity.makeFinishingLocked();
        stack.finishCurrentActivityLocked(activity, ActivityStack.FINISH_AFTER_VISIBLE,
                false /* oomAdj */, "finishCurrentActivity");
        return activity;
    }

    @Test
    public void testShouldSleepActivities() {
        // When focused activity and keyguard is going away, we should not sleep regardless
        // of the display state
        verifyShouldSleepActivities(true /* focusedStack */, true /*keyguardGoingAway*/,
                true /* displaySleeping */, false /* expected*/);

        // When not the focused stack, defer to display sleeping state.
        verifyShouldSleepActivities(false /* focusedStack */, true /*keyguardGoingAway*/,
                true /* displaySleeping */, true /* expected*/);

        // If keyguard is going away, defer to the display sleeping state.
        verifyShouldSleepActivities(true /* focusedStack */, false /*keyguardGoingAway*/,
                true /* displaySleeping */, true /* expected*/);
        verifyShouldSleepActivities(true /* focusedStack */, false /*keyguardGoingAway*/,
                false /* displaySleeping */, false /* expected*/);
    }

    @Test
    public void testStackOrderChangedOnRemoveStack() {
        StackOrderChangedListener listener = new StackOrderChangedListener();
        mDefaultDisplay.registerStackOrderChangedListener(listener);
        try {
            mDefaultDisplay.removeChild(mStack);
        } finally {
            mDefaultDisplay.unregisterStackOrderChangedListener(listener);
        }
        assertTrue(listener.mChanged);
    }

    @Test
    public void testStackOrderChangedOnAddPositionStack() {
        mDefaultDisplay.removeChild(mStack);

        StackOrderChangedListener listener = new StackOrderChangedListener();
        mDefaultDisplay.registerStackOrderChangedListener(listener);
        try {
            mDefaultDisplay.addChild(mStack, 0);
        } finally {
            mDefaultDisplay.unregisterStackOrderChangedListener(listener);
        }
        assertTrue(listener.mChanged);
    }

    @Test
    public void testStackOrderChangedOnPositionStack() {
        StackOrderChangedListener listener = new StackOrderChangedListener();
        try {
            final TestActivityStack fullscreenStack1 = createStackForShouldBeVisibleTest(
                    mDefaultDisplay, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                    true /* onTop */);
            mDefaultDisplay.registerStackOrderChangedListener(listener);
            mDefaultDisplay.positionChildAtBottom(fullscreenStack1);
        } finally {
            mDefaultDisplay.unregisterStackOrderChangedListener(listener);
        }
        assertTrue(listener.mChanged);
    }

    private void verifyShouldSleepActivities(boolean focusedStack,
            boolean keyguardGoingAway, boolean displaySleeping, boolean expected) {
        final ActivityDisplay display = mock(ActivityDisplay.class);
        final KeyguardController keyguardController = mSupervisor.getKeyguardController();

        doReturn(display).when(mRootActivityContainer).getActivityDisplay(anyInt());
        doReturn(keyguardGoingAway).when(keyguardController).isKeyguardGoingAway();
        doReturn(displaySleeping).when(display).isSleeping();
        doReturn(focusedStack).when(mStack).isFocusedStackOnDisplay();

        assertEquals(expected, mStack.shouldSleepActivities());
    }

    private static class StackOrderChangedListener
            implements ActivityDisplay.OnStackOrderChangedListener {
        public boolean mChanged = false;

        @Override
        public void onStackOrderChanged() {
            mChanged = true;
        }
    }
}
