package com.liang.study.t1;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.liang.study.util.RedisUtils;
import redis.clients.jedis.Jedis;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author liangyt
 */
public class RedisCanalClientExample {
    public static final Integer _60SECONDS = 60;

    public static void main(String args[]) {

        // 创建链接canal服务端
        CanalConnector connector = CanalConnectors.newSingleConnector(new InetSocketAddress("192.168.111.147",
                11111), "example", "", "");
        int batchSize = 1000;
        int emptyCount = 0;
        System.out.println("----------------程序启动，开始监听mysql的变化：");
        try {
            connector.connect();
            connector.subscribe(".*\\..*");
            //connector.subscribe("db2020.t_order");
            connector.rollback();
            int totalEmptyCount = 10 * _60SECONDS;

            while (emptyCount < totalEmptyCount) {
                Message message = connector.getWithoutAck(batchSize); // 获取指定数量的数据
                long batchId = message.getId();
                int size = message.getEntries().size();
                if (batchId == -1 || size == 0) {
                    emptyCount++;
                    try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { e.printStackTrace(); }
                } else {
                    emptyCount = 0;
                    printEntry(message.getEntries());
                }
                connector.ack(batchId); // 提交确认
                // connector.rollback(batchId); // 处理失败, 回滚数据
            }
            System.out.println("empty too many times, exit");
        } finally {
            connector.disconnect();
        }
    }

    private static void printEntry(List<CanalEntry.Entry> entrys) {
        for (CanalEntry.Entry entry : entrys) {
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                continue;
            }

            CanalEntry.RowChange rowChage = null;
            try {
                rowChage = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("ERROR ## parser of eromanga-event has an error,data:" + entry.toString(),e);
            }

            CanalEntry.EventType eventType = rowChage.getEventType();
            System.out.println(String.format("================ binlog[%s:%s] , name[%s,%s] , eventType : %s",
                    entry.getHeader().getLogfileName(), entry.getHeader().getLogfileOffset(),
                    entry.getHeader().getSchemaName(), entry.getHeader().getTableName(), eventType));

            for (CanalEntry.RowData rowData : rowChage.getRowDatasList()) {
                if (eventType == CanalEntry.EventType.INSERT) {
                    redisInsert(rowData.getAfterColumnsList());
                } else if (eventType == CanalEntry.EventType.DELETE) {
                    redisDelete(rowData.getBeforeColumnsList());
                } else {//EventType.UPDATE
                    redisUpdate(rowData.getAfterColumnsList());
                }
            }
        }
    }

    private static void redisInsert(List<CanalEntry.Column> columns)
    {
        JSONObject jsonObject = new JSONObject();
        for (CanalEntry.Column column : columns)
        {
            System.out.println(column.getName() + " : " + column.getValue() + "    insert=" + column.getUpdated());
            jsonObject.put(column.getName(),column.getValue());
        }
        if(columns.size() > 0)
        {
            try(Jedis jedis = RedisUtils.getJedis())
            {
                jedis.set(columns.get(0).getValue(),jsonObject.toJSONString());
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private static void redisDelete(List<CanalEntry.Column> columns)
    {
        JSONObject jsonObject = new JSONObject();
        for (CanalEntry.Column column : columns)
        {
            System.out.println(column.getName() + " : " + column.getValue() + "    delete=" + column.getUpdated());
            jsonObject.put(column.getName(),column.getValue());
        }
        if(columns.size() > 0)
        {
            try(Jedis jedis = RedisUtils.getJedis())
            {
                jedis.del(columns.get(0).getValue());
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private static void redisUpdate(List<CanalEntry.Column> columns)
    {
        JSONObject jsonObject = new JSONObject();
        for (CanalEntry.Column column : columns)
        {
            System.out.println(column.getName() + " : " + column.getValue() + "    update=" + column.getUpdated());
            jsonObject.put(column.getName(),column.getValue());
        }
        if(columns.size() > 0)
        {
            try(Jedis jedis = RedisUtils.getJedis())
            {
                jedis.set(columns.get(0).getValue(),jsonObject.toJSONString());
                System.out.println("---------update after: "+jedis.get(columns.get(0).getValue()));
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        long startTime = System.currentTimeMillis();

        long endTime = System.currentTimeMillis();
        System.out.println("----costTime: "+(endTime - startTime) +" 毫秒");

    }
}
