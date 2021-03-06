package net.rdrei.android.buildtimetracker

import groovy.mock.interceptor.MockFor
import net.rdrei.android.buildtimetracker.reporters.AbstractBuildTimeTrackerReporter
import net.rdrei.android.buildtimetracker.util.Clock
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.TaskState
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull

class TimingRecorderTest {

    Task mockTask(String path) {
        def mockTask = new MockFor(Task)
        mockTask.demand.getPath { path }

        mockTask.proxyInstance()
    }

    MockFor mockClock(int ms) {
        def mockClock = new MockFor(Clock)

        mockClock.demand.getTimeInMs { ms }

        mockClock
    }

    BuildTimeTrackerPlugin buildPlugin() {
        new BuildTimeTrackerPlugin()
    }

    @Test
    void recordsTaskPaths() {
        mockClock(0).use {
            def plugin = buildPlugin()
            TimingRecorder listener = new TimingRecorder(plugin)
            Task task = mockTask "test"
            TaskState state = new TaskStateBuilder().build()

            listener.buildStarted null
            listener.beforeExecute task
            listener.afterExecute task, state

            assertEquals(["test"], listener.getTasks())
        }
    }

    @Test
    void recordsTaskTiming() {
        mockClock(123).use {
            TimingRecorder listener = new TimingRecorder()
            Task task = mockTask "test"
            TaskState state = new TaskStateBuilder().build()

            listener.buildStarted null
            listener.beforeExecute task
            listener.afterExecute task, state

            Timing timing = listener.getTiming "test"
            assertEquals 123, timing.ms
        }
    }

    @Test
    void buildFinishes() {
        mockClock(0).use {
            def plugin = buildPlugin()

            TimingRecorder listener = new TimingRecorder(plugin)
            Task task = mockTask "test"
            TaskState state = new TaskStateBuilder().build()

            listener.buildStarted null
            listener.beforeExecute task
            listener.afterExecute task, state
            listener.buildFinished null
        }
    }

    @Test
    void callsReportersOnBuildFinished() {
        def mockReporter = new MockFor(AbstractBuildTimeTrackerReporter)
        def mockLogger = new MockFor(Logger)
        mockReporter.demand.run { timings ->
            assertEquals 1, timings.size
            assertEquals "test", timings.get(0).path
            assertEquals 123, timings.get(0).ms
        }
        mockReporter.demand.onBuildResult { result ->
            assertNull result
        }
        def proxyReporter = mockReporter.proxyInstance([[:], mockLogger.proxyInstance()] as Object[])

        def mockPlugin = new MockFor(BuildTimeTrackerPlugin)
        mockPlugin.demand.getReporters { [ proxyReporter ] }
        def proxyPlugin = mockPlugin.proxyInstance()

        mockClock(123).use {
            TimingRecorder listener = new TimingRecorder(proxyPlugin)
            Task task = mockTask "test"
            TaskState state = new TaskStateBuilder().build()

            listener.buildStarted null
            listener.beforeExecute task
            listener.afterExecute task, state
            listener.buildFinished null

            mockReporter.verify proxyReporter
            mockPlugin.verify proxyPlugin
        }
    }
}
