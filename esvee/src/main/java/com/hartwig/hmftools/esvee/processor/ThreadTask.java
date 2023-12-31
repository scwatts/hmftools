package com.hartwig.hmftools.esvee.processor;

import static java.lang.String.format;

import static com.hartwig.hmftools.esvee.SvConfig.SV_LOGGER;

import java.util.List;

import com.hartwig.hmftools.common.utils.PerformanceCounter;

public class ThreadTask extends Thread
{
    protected final PerformanceCounter mPerfCounter;

    public ThreadTask(final String taskName)
    {
        mPerfCounter = new PerformanceCounter(taskName);
    }

    public PerformanceCounter getPerfCounter() { return mPerfCounter; }

    public void stopCheckLog(final String eventDetails, final double threshold)
    {
        mPerfCounter.stop();

        if(mPerfCounter.getLastTime() > threshold)
        {
            SV_LOGGER.info("task({}) exceeds perf log time({})", eventDetails, format("%.3f", mPerfCounter.getLastTime()));
        }
    }

    public static PerformanceCounter mergePerfCounters(final List<ThreadTask> tasks)
    {
        PerformanceCounter first = tasks.get(0).getPerfCounter();

        if(tasks.size() == 1)
            return first;

        PerformanceCounter combined = new PerformanceCounter(first.getName());

        tasks.forEach(x -> combined.merge(x.getPerfCounter()));
        return combined;
    }

}
