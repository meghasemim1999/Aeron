import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.HdrHistogram.Histogram;
import org.agrona.BitUtil;
import org.agrona.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.UnsafeBuffer;

public class ReceiveAgent implements Agent
{
    private static final int FRAGMENT_COUNT_LIMIT = Configuration.FRAGMENT_COUNT_LIMIT;
    private static long recvCounter = 0L;
    private static ShutdownSignalBarrier barrier;
    private static Histogram histogram;
    private final Subscription subscription;
    private static long messageCount;
    private static final int MESSAGE_LENGTH = Configuration.MESSAGE_LENGTH;
    private static final FragmentHandler fragmentHandler = new FragmentAssembler(ReceiveAgent::pongHandler);;
    private static final UnsafeBuffer OFFER_BUFFER = new UnsafeBuffer(
            BufferUtil.allocateDirectAligned(MESSAGE_LENGTH, BitUtil.CACHE_LINE_LENGTH));

    public ReceiveAgent(final Subscription subscription, Histogram histogram, ShutdownSignalBarrier barrier, long messageCount)
    {
        this.subscription = subscription;
        ReceiveAgent.histogram = histogram;
        ReceiveAgent.barrier = barrier;
        ReceiveAgent.messageCount = messageCount;
    }

    @Override
    public int doWork()
    {
        subscription.poll(fragmentHandler, FRAGMENT_COUNT_LIMIT);
        return 0;
    }

    private static void pongHandler(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        final long pingTimestamp = buffer.getLong(offset);
        final long rttNs = System.nanoTime() - pingTimestamp;

        recvCounter++;
//        if(warmed)
//        if(recvCounter % 10000 == 0)
//        {
//            System.out.println("RECV: " + recvCounter);
//            System.out.println("RTT: " + rttNs);
//        }

        histogram.recordValue(rttNs);

        if (recvCounter >= messageCount)
        {
            barrier.signal();
        }
    }

    @Override
    public String roleName()
    {
        return "sender";
    }
}