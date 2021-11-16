import io.aeron.Publication;
import org.agrona.BitUtil;
import org.agrona.BufferUtil;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.UnsafeBuffer;

public class SendAgent implements Agent
{
    private final Publication publication;
    private final long messageCount;
    private static final int MESSAGE_LENGTH = Configuration.MESSAGE_LENGTH;
    private static final UnsafeBuffer OFFER_BUFFER = new UnsafeBuffer(
            BufferUtil.allocateDirectAligned(MESSAGE_LENGTH, BitUtil.CACHE_LINE_LENGTH));
    private long currentCountItem = 0L;

    public SendAgent(final Publication publication, long messageCount)
    {
        this.publication = publication;
        this.messageCount = messageCount;
    }

    @Override
    public int doWork() throws InterruptedException {
        if (currentCountItem > messageCount)
        {
            return 0;
        }

        for (long i = 0; i < messageCount; i++)
        {
            do
            {
                OFFER_BUFFER.putLong(0, System.nanoTime());
            }
            while (publication.offer(OFFER_BUFFER, 0, MESSAGE_LENGTH, null) < 0L);

//            sendCounter++;
//            if(sendCounter % 10000 == 0)
//            {
//                System.out.println("SENT: " + sendCounter);
//            }

            currentCountItem += 1;

            Thread.sleep(0, 100000);
        }

        return 0;
    }

    @Override
    public String roleName()
    {
        return "sender";
    }
}