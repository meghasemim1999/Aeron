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

import io.aeron.*;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import io.aeron.samples.SampleConfiguration;
import org.HdrHistogram.Histogram;
import org.agrona.*;
import org.agrona.concurrent.*;
import org.agrona.console.ContinueBarrier;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ping component of Ping-Pong latency test recorded to a histogram to capture full distribution.
 * <p>
 * Initiates messages sent to {@link MyPong} and records times.
 *
 * @see MyPong
 */
public class ThreadedPing {
    private static final int PING_STREAM_ID = SampleConfiguration.PING_STREAM_ID;
    private static final int PONG_STREAM_ID = SampleConfiguration.PONG_STREAM_ID;
    private static final long NUMBER_OF_MESSAGES = 10_000_000L;
    private static final long WARMUP_NUMBER_OF_MESSAGES = 100_000L;
    private static long sendCounter = 0L;
    //    private static AtomicLong recvCounter = new AtomicLong(0);
    private static boolean warmed = false;
    private static final int WARMUP_NUMBER_OF_ITERATIONS = 10;
    //    private static final int MESSAGE_LENGTH = SampleConfiguration.MESSAGE_LENGTH;
    private static final int MESSAGE_LENGTH = 32;
    private static final int FRAGMENT_COUNT_LIMIT = 10;
    private static final boolean EMBEDDED_MEDIA_DRIVER = true;
    private static final boolean EXCLUSIVE_PUBLICATIONS = true;
    //    public static final String PING_CHANNEL = "aeron:udp?endpoint=172.16.30.101:20125";
//    public static final String PONG_CHANNEL = "aeron:udp?endpoint=172.16.30.103:20124";
    public static final String PING_CHANNEL = "aeron:udp?endpoint=localhost:20125";
    public static final String PONG_CHANNEL = "aeron:udp?endpoint=localhost:20126";

    private static final ExpandableDirectByteBuffer OFFER_BUFFER = new ExpandableDirectByteBuffer(MESSAGE_LENGTH);
    private static final Histogram HISTOGRAM = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
    private static final CountDownLatch LATCH = new CountDownLatch(1);
    private static final IdleStrategy POLLING_IDLE_STRATEGY = new NoOpIdleStrategy();
    private static ArrayList<Long> offeredPositions = new ArrayList<>();
    private static int recvIndex = 0;
    private static final ShutdownSignalBarrier sbarrier = new ShutdownSignalBarrier();

    /**
     * Main method for launching the process.
     *
     * @param args passed to the process.
     * @throws InterruptedException if the thread is interrupted.
     */
    public static void main(final String[] args) throws InterruptedException {
        final MediaDriver driver = EMBEDDED_MEDIA_DRIVER ? MediaDriver.launchEmbedded() : MediaDriver.launch();

        driver.context().threadingMode(ThreadingMode.DEDICATED);

        final Aeron.Context ctx = new Aeron.Context().availableImageHandler(ThreadedPing::availablePongImageHandler);
        final FragmentHandler fragmentHandler = new FragmentAssembler(ThreadedPing::pongHandler);
        final ExecutorService executor = Executors.newFixedThreadPool(2);

        if (EMBEDDED_MEDIA_DRIVER) {
            ctx.aeronDirectoryName(driver.aeronDirectoryName());
        }

        System.out.println("Publishing Ping at " + PING_CHANNEL + " on stream id " + PING_STREAM_ID);
        System.out.println("Subscribing Pong at " + PONG_CHANNEL + " on stream id " + PONG_STREAM_ID);
        System.out.println("Message length of " + MESSAGE_LENGTH + " bytes");
        System.out.println("Using exclusive publications " + EXCLUSIVE_PUBLICATIONS);

        try (Aeron aeron = Aeron.connect(ctx);
             Subscription subscription = aeron.addSubscription(PONG_CHANNEL, PONG_STREAM_ID);
             Publication publication = EXCLUSIVE_PUBLICATIONS ?
                     aeron.addExclusivePublication(PING_CHANNEL, PING_STREAM_ID) :
                     aeron.addPublication(PING_CHANNEL, PING_STREAM_ID)) {
            System.out.println("Waiting for new image from Pong...");
            LATCH.await();

            System.out.println(
                    "Warming up... " + WARMUP_NUMBER_OF_ITERATIONS +
                            " iterations of " + WARMUP_NUMBER_OF_MESSAGES + " messages");

            for (int i = 0; i < WARMUP_NUMBER_OF_ITERATIONS; i++) {
                roundTripMessages(fragmentHandler, publication, subscription, WARMUP_NUMBER_OF_MESSAGES);
                Thread.yield();
            }

            warmed = true;

            Thread.sleep(100);
            final ContinueBarrier barrier = new ContinueBarrier("Execute again?");
            AtomicBoolean running = new AtomicBoolean(true);
            final CountDownLatch lat = new CountDownLatch(2);
            do {
                HISTOGRAM.reset();
//                recvCounter.set(0);
                System.out.println("Pinging " + NUMBER_OF_MESSAGES + " messages");

                executor.execute(() -> {
                    try {
                        roundTripMessagesPublisher(publication, NUMBER_OF_MESSAGES);
//                        lat.countDown();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });

                executor.execute(() -> {
                    try {
//                        Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
                        recvIndex = 0;
                        HISTOGRAM.reset();
                        roundTripMessagesSubscriber(fragmentHandler, subscription, running);
                        System.out.println("Histogram of RTT latencies in microseconds.");

                        HISTOGRAM.outputPercentileDistribution(System.out, 1000.0);
                        System.out.println("mean: " + HISTOGRAM.getMean());
                        System.out.println("50 percentile: " + HISTOGRAM.getValueAtPercentile(50.00));
//                        lat.countDown();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });


//
//                executor.execute(() -> {
//                    try {
//                        roundTripMessagesSubscriber(fragmentHandler, subscription, running);
//                        lat.countDown();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                });
//
//                executor.execute(() -> {
//                    try {
//                        roundTripMessagesSubscriber(fragmentHandler, subscription, running);
//                        lat.countDown();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                });
//
//                executor.execute(() -> {
//                    try {
//                        roundTripMessagesSubscriber(fragmentHandler, subscription, running);
//                        lat.countDown();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                });
//
//                executor.execute(() -> {
//                    try {
//                        roundTripMessagesSubscriber(fragmentHandler, subscription, running);
//                        lat.countDown();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                });
//
//                executor.execute(() -> {
//                    try {
//                        roundTripMessagesSubscriber(fragmentHandler, subscription, running);
//                        lat.countDown();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                });
//
//                executor.execute(() -> {
//                    try {
//                        roundTripMessagesSubscriber(fragmentHandler, subscription, running);
//                        lat.countDown();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                });

//                lat.await();

//                System.out.println("Histogram of RTT latencies in microseconds.");
//
//                HISTOGRAM.outputPercentileDistribution(System.out, 1000.0);
//                System.out.println("mean: " + HISTOGRAM.getMean());
//                System.out.println(HISTOGRAM.getValueAtPercentile(50.00));
            }
            while (barrier.await());
        }

        CloseHelper.close(driver);
    }

    private static void roundTripMessagesPublisher(
            final Publication publication,
            final long count) throws InterruptedException {

        for (long i = 0; i < count; i++) {
//            long offeredPosition;

            OFFER_BUFFER.putLong(0, System.nanoTime());
            long result = publication.offer(OFFER_BUFFER, 0, MESSAGE_LENGTH);

            if (result < 0)
            {
                System.out.println("Offer failed due to: " + result);
                i--;
                continue;
            }
//            offeredPositions.add(offeredPosition);
//            sendCounter++;
//            if (sendCounter % 10000 == 0) {
//                System.out.println("SENT: " + sendCounter);
//            }

            long start = System.nanoTime();
            while (System.nanoTime() - start < 50000)
            {
                Thread.yield();
            }

//            System.out.println(System.nanoTime() - start);
//            Thread.sleep(0, 1000);
        }
    }


    private static void roundTripMessagesSubscriber(
            final FragmentHandler fragmentHandler,
            final Subscription subscription,
            AtomicBoolean running) throws InterruptedException {

        while (!subscription.isConnected()) {
            Thread.yield();
        }

//        IdleStrategy idleStrategy = new BackoffIdleStrategy();

        while (recvIndex < NUMBER_OF_MESSAGES) {
//            mtx.lock();
//            idleStrategy.idle(subscription.poll(fragmentHandler, FRAGMENT_COUNT_LIMIT));
            final int fragments = subscription.poll(fragmentHandler, FRAGMENT_COUNT_LIMIT);
//            mtx.unlock();
            POLLING_IDLE_STRATEGY.idle(fragments);
        }
    }

    private static void roundTripMessages(
            final FragmentHandler fragmentHandler,
            final Publication publication,
            final Subscription subscription,
            final long count) throws InterruptedException {
        while (!subscription.isConnected()) {
            Thread.yield();
        }

        final Image image = subscription.imageAtIndex(0);

        for (long i = 0; i < count; i++) {
            long offeredPosition;

            do {
                OFFER_BUFFER.putLong(0, System.nanoTime());
            }
            while ((offeredPosition = publication.offer(OFFER_BUFFER, 0, MESSAGE_LENGTH, null)) < 0L);

            while (image.position() < offeredPosition) {
                final int fragments = image.poll(fragmentHandler, FRAGMENT_COUNT_LIMIT);
                POLLING_IDLE_STRATEGY.idle(fragments);
            }
        }
    }

    private static void pongHandler(final DirectBuffer buffer, final int offset, final int length, final Header header) {
        final long now = System.nanoTime();
        final long pingTimestamp = buffer.getLong(offset);
        final long rttNs = now - pingTimestamp;
//        System.out.println("length is: " + length);
//        recvCounter.incrementAndGet();
        recvIndex++;
//        if (recvCounter.longValue() % 10000 == 0) {
//            System.out.println("RECV: " + recvCounter);
//            System.out.println("RTT: " + rttNs);
//        }
        if (warmed)
            HISTOGRAM.recordValue(rttNs);
    }

    private static void availablePongImageHandler(final Image image) {
        final Subscription subscription = image.subscription();
        System.out.format(
                "Available image: channel=%s streamId=%d session=%d%n",
                subscription.channel(), subscription.streamId(), image.sessionId());

        if (PONG_STREAM_ID == subscription.streamId() && PONG_CHANNEL.equals(subscription.channel())) {
            LATCH.countDown();
        }
    }
}