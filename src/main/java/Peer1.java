/*
 * Copyright 2014-2021 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.samples.MultipleSubscribersWithFragmentAssembly;
import io.aeron.samples.SampleConfiguration;
import io.aeron.samples.SamplesUtil;
import org.HdrHistogram.Histogram;
import org.agrona.BufferUtil;
import org.agrona.CloseHelper;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Peer1
{
    private static final int STREAM_ID = 1001;
    private static final int STREAM_ID2 = 1005;
    private static final String CHANNEL = "aeron:udp?endpoint=localhost:20121";
    private static final String CHANNEL2 = "aeron:udp?endpoint=localhost:20125";
    private static final long NUMBER_OF_MESSAGES = 110000L;
    private static final long NUMBER_OF_WARMUP_MESSAGES = 10000L;
    private static long rttsCount = 0L;
    private static final int FRAGMENT_COUNT_LIMIT = 10;
    private static final boolean EMBEDDED_MEDIA_DRIVER = true;
    private static final LinkedHashSet<Long> elapsedTimes = new LinkedHashSet<Long>();
    private static final Histogram HISTOGRAM = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);

    /**
     * Main method for launching the process.
     *
     * @param args passed to the process.
     */
    public static void main(final String[] args)
    {
        System.setProperty("aeron.threading.mode", "DEDICATED");
//        System.setProperty("aeron.dir", "R:\\Aeron");
        System.out.println("Subscribing to " + CHANNEL2 + " on stream id " + STREAM_ID2);
        final MediaDriver driver = EMBEDDED_MEDIA_DRIVER ? MediaDriver.launchEmbedded() : MediaDriver.launch();
        final Aeron.Context ctx = new Aeron.Context()
                .availableImageHandler(SamplesUtil::printAvailableImage)
                .unavailableImageHandler(SamplesUtil::printUnavailableImage);

        ctx.aeronDirectoryName(driver.aeronDirectoryName());

        final FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
            rttsCount++;
            if(rttsCount > NUMBER_OF_WARMUP_MESSAGES)
            {
                long receivedTime = System.nanoTime();
                String msg = buffer.getStringWithoutLengthAscii(offset, length);
                long sentTime = Long.parseLong(msg);
                long elapsedTime = receivedTime - sentTime;
                HISTOGRAM.recordValue(elapsedTime);
            }
            else
            {
                System.out.println(rttsCount);
            }
//            System.out.println(elapsedTime);
        };

        final AtomicBoolean running = new AtomicBoolean(true);

        // Register a SIGINT handler for graceful shutdown.
        SigInt.register(() -> running.set(false));

        // Create an Aeron instance using the configured Context and create a
        // Subscription on that instance that subscribes to the configured
        // channel and stream ID.
        // The Aeron and Subscription classes implement "AutoCloseable" and will automatically
        // clean up resources when this try block is finished
        try (Aeron aeron = Aeron.connect(ctx);
             Subscription subscription = aeron.addSubscription(CHANNEL2, STREAM_ID2);
             Publication publication = aeron.addPublication(CHANNEL, STREAM_ID))
        {
            IdleStrategy idleStrategy = new SleepingIdleStrategy(10);
            FragmentAssembler assembler = new FragmentAssembler(fragmentHandler);
            final UnsafeBuffer buffer = new UnsafeBuffer(BufferUtil.allocateDirectAligned(16, 64));

            HISTOGRAM.reset();

            for (int i = 0; i < NUMBER_OF_MESSAGES; i++)
            {
                int length = buffer.putLongAscii(0, System.nanoTime());
                publication.offer(buffer, 0, length);
                int fragmentsRead = subscription.poll(assembler, FRAGMENT_COUNT_LIMIT);
                idleStrategy.idle(fragmentsRead);
            }

            System.out.println("Shutting down...");
        }

        CloseHelper.close(driver);

        HISTOGRAM.outputPercentileDistribution(System.out, 1000.0);
//
//        long mean = 0L;
//        long count = 0L;
//
//        while (elapsedTimes.iterator().hasNext())
//        {
//            mean += elapsedTimes.iterator().next();
//            count++;
//        }
//
//        System.out.println(mean / (count) + " is mean elapsed time of " + count + " messages.");
    }
}
