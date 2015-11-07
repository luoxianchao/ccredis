package cc.utils.ccredis;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

/**
 * redis��ʼ��������
 * 
 * @author chuan
 * @date 2015-11-4
 */

@SuppressWarnings( { "unchecked" })
public class RedisFactory {

	private static Logger logger = Logger.getLogger(RedisFactory.class.getName());

	/**
	 * �����ļ�·��
	 */
	private String confPath = "/redis.yaml";

	/**
	 * Ĭ�ϳ�ʱʱ��
	 */
	private final int timeout = Protocol.DEFAULT_TIMEOUT;

	/**
	 * ip�˿�����У��
	 */
	private static final Pattern ipPortPattern = Pattern
			.compile("(2[5][0-5]|2[0-4]\\d|1\\d{2}|\\d{1,2})\\.(25[0-5]|2[0-4]\\d|1\\d{2}|\\d{1,2})\\.(25[0-5]|2[0-4]\\d|1\\d{2}|\\d{1,2})\\.(25[0-5]|2[0-4]\\d|1\\d{2}|\\d{1,2}):\\d{0,5}");

	/**
	 * redis������Ϣmap
	 */
	private static final Map redisConf = Maps.newConcurrentMap();

	/**
	 * redisʵ��map ʵ��һ��redis pool
	 */
	private static final Map<String, JedisPool> redisPoools = Maps.newConcurrentMap();

	/**
	 * Ĭ��redisʵ������
	 */
	private static final String DEFAULT_REDIS = "default";

	public RedisFactory() {
		initialize();
	}

	private static final RedisFactory redisFactory = new RedisFactory();

	public static RedisFactory getInstance() {
		return redisFactory;
	}

	/**
	 * redis��ʼ�����
	 */
	public void initialize() {

		initRedisConfInfo();

		checkRedisConfInfo();

		initRedisPool();

	}

	/**
	 * ��ȡjedis����
	 * @param key
	 * @return
	 */
	public Jedis getJedis(String key) {
		try {
			return jedisFromPool(key).getResource();
		} catch (Exception e) {
			getJedisPool(key);
			return jedisFromPool(key).getResource();
		}
	}

	/**
	 * �����ӳضϿ���ʼ������ synchronized �ŶӼ�������ֹ����
	 * 
	 * @param key
	 */
	private synchronized void getJedisPool(String key) {
		try {
			Jedis jedis = jedisFromPool(key).getResource();
			jedis.close();
			return;
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		initRedis(key);
	}

	private JedisPool jedisFromPool(String key) {
		return redisPoools.containsKey(key) ? redisPoools.get(key) : redisPoools.get(DEFAULT_REDIS);
	}

	/**
	 * ��ʼ��redis���ӳ�
	 */
	private void initRedisPool() {
		Set<Entry> entrySet = redisConf.entrySet();
		for (Entry entry : entrySet) {
			initRedis((String) entry.getKey());
		}
	}

	/**
	 * @param entry
	 */
	private void initRedis(String key) {
		Map value = (Map) redisConf.get(key);
		try {
			URI server = new URI("redis://"+value.get("server"));
			Map poolInfo = (Map) value.get("pool");
			JedisPool pool = new JedisPool(getPoolConfigure(poolInfo),server,timeout);
			redisPoools.put(key, pool);
			//�ж�jedis pool �ǵĳ�ʼ�����
			Jedis jedis = pool.getResource();
			jedis.close();
			logger.info("init redis pool success:" + key);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param poolInfo
	 * @return
	 */
	private JedisPoolConfig getPoolConfigure(Map poolInfo) {
		JedisPoolConfig config = new JedisPoolConfig();
		config.setMaxTotal((Integer) poolInfo.get("maxTotal"));
		config.setMaxIdle((Integer) poolInfo.get("maxIdle"));
		config.setMaxWaitMillis(Long.parseLong(String.valueOf(poolInfo.get("maxWait"))));
		return config;
	}

	/**
	 * У��������Ϣ
	 */
	private void checkRedisConfInfo() {
		Preconditions.checkNotNull(redisConf);
		Preconditions.checkArgument(!redisConf.isEmpty());
		Preconditions.checkNotNull(redisConf.get(DEFAULT_REDIS));
		Set<Entry> entrySet = redisConf.entrySet();
		for (Entry entry : entrySet) {
			Map value = (Map) entry.getValue();
			String server = (String) value.get("server");
			Preconditions.checkArgument(!Strings.isNullOrEmpty(server));
			if (!ipPortPattern.matcher(server).matches()) {
				throw new RuntimeException("redis servers ip configure error: " + server + " is error.");
			}
		}

	}

	/**
	 * ��ʼ��������Ϣ
	 */
	private void initRedisConfInfo() {
		try {
			Reader reader = new InputStreamReader(RedisFactory.class.getResourceAsStream(confPath), "UTF-8");
			Map load = new Yaml().loadAs(reader, HashMap.class);
			redisConf.clear();
			redisConf.putAll(load);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Redis configure info error:" + e.getMessage());
		}

	}
}
