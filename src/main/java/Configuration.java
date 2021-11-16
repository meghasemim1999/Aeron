import org.agrona.SystemUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.status.StatusIndicator;

public class Configuration {
    public static final String CHANNEL_PROP = "aeron.sample.channel";
    public static final String STREAM_ID_PROP = "aeron.sample.streamId";
    public static final String PING_CHANNEL_PROP = "aeron.sample.ping.channel";
    public static final String PONG_CHANNEL_PROP = "aeron.sample.pong.channel";
    public static final String PING_STREAM_ID_PROP = "aeron.sample.ping.streamId";
    public static final String PONG_STREAM_ID_PROP = "aeron.sample.pong.streamId";
    public static final String WARMUP_NUMBER_OF_MESSAGES_PROP = "aeron.sample.warmup.messages";
    public static final String WARMUP_NUMBER_OF_ITERATIONS_PROP = "aeron.sample.warmup.iterations";
    public static final String RANDOM_MESSAGE_LENGTH_PROP = "aeron.sample.randomMessageLength";
    public static final String FRAME_COUNT_LIMIT_PROP = "aeron.sample.frameCountLimit";
    public static final String MESSAGE_LENGTH_PROP = "aeron.sample.messageLength";
    public static final String NUMBER_OF_MESSAGES_PROP = "aeron.sample.messages";
    public static final String LINGER_TIMEOUT_MS_PROP = "aeron.sample.lingerTimeout";
    public static final String EMBEDDED_MEDIA_DRIVER_PROP = "aeron.sample.embeddedMediaDriver";
    public static final String EXCLUSIVE_PUBLICATIONS_PROP = "aeron.sample.exclusive.publications";
    public static final String IDLE_STRATEGY_PROP = "aeron.sample.idleStrategy";
    public static final String INFO_FLAG_PROP = "aeron.sample.info";
    public static final String CHANNEL = System.getProperty("aeron.sample.channel", "aeron:udp?endpoint=localhost:20121");
    public static final String PING_CHANNEL = System.getProperty("aeron.sample.ping.channel", "aeron:udp?endpoint=localhost:20123");
    public static final String PONG_CHANNEL = System.getProperty("aeron.sample.pong.channel", "aeron:udp?endpoint=localhost:20124");
    public static final String IDLE_STRATEGY_NAME = System.getProperty("aeron.sample.idleStrategy", "org.agrona.concurrent.BusySpinIdleStrategy");
    public static final boolean EMBEDDED_MEDIA_DRIVER = "true".equals(System.getProperty("aeron.sample.embeddedMediaDriver"));
    public static final boolean RANDOM_MESSAGE_LENGTH = "true".equals(System.getProperty("aeron.sample.randomMessageLength"));
    public static final boolean INFO_FLAG = "true".equals(System.getProperty("aeron.sample.info"));
    public static final int STREAM_ID = Integer.getInteger("aeron.sample.streamId", 1001);
    public static final int PING_STREAM_ID = Integer.getInteger("aeron.sample.ping.streamId", 1002);
    public static final int PONG_STREAM_ID = Integer.getInteger("aeron.sample.pong.streamId", 1003);
    public static final int FRAGMENT_COUNT_LIMIT = 10;
    public static final int MESSAGE_LENGTH = 32;
    public static final int WARMUP_NUMBER_OF_ITERATIONS = Integer.getInteger("aeron.sample.warmup.iterations", 10);
    public static final long WARMUP_NUMBER_OF_MESSAGES = Long.getLong("aeron.sample.warmup.messages", 10000L);
    public static final long NUMBER_OF_MESSAGES = Long.getLong("aeron.sample.messages", 10000000L);
    public static final long LINGER_TIMEOUT_MS = Long.getLong("aeron.sample.lingerTimeout", 0L);
    public static final boolean EXCLUSIVE_PUBLICATIONS = "true".equals(System.getProperty("aeron.sample.exclusive.publications"));

    public Configuration() {
    }

    public static IdleStrategy newIdleStrategy() {
        return io.aeron.driver.Configuration.agentIdleStrategy(IDLE_STRATEGY_NAME, (StatusIndicator)null);
    }
}
