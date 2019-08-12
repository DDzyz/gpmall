package com.gpmall.commons.lock.impl.zk;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Description: 分布式锁---后台线程
 * 用来删除无效节点
 * @date 2019年8月12日
 * @author msl 1015952139
 */
public class LockBackGroundThread extends Thread{

    private Logger logger = LoggerFactory.getLogger(getClass());

    CuratorFramework client;

    protected LockBackGroundThread(CuratorFramework client){
        this.client = client;
        this.setDaemon(true);
        this.setName("ZkMutexDistributedLock---background");
    }

    @Override
    public synchronized void run() {
        super.run();
        try {
            while (true){
                //TODO  后期可以通过配置中心  配置
                LockBackGroundConf conf = new LockBackGroundConf();
                deleteInvalidNode(conf);
                // 默认一小时执行一次(配置中心可配)
                Thread.currentThread().wait(conf.getFrequency()*1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void deleteInvalidNode(LockBackGroundConf conf) throws Exception{
        String projectDir = ZkMutexDistributedLockFactory.lockPath + ZkMutexDistributedLockFactory.projectName;
        Stat exitDir = client.checkExists().forPath(projectDir.substring(0, projectDir.length()-1));
        if(exitDir == null){
            logger.error("根目录尚未创建，本次清理结束--" + projectDir);
            return;
        }
        List<String> paths = client.getChildren().forPath(projectDir.substring(0, projectDir.length()-1));
        Date date = new Date();
        paths.forEach(currPath -> {
            try{
                Stat stat = new Stat();
                client.getData().storingStatIn(stat).forPath(projectDir + currPath);
                // 默认删除一天前无效的数据。 子节点为0，说明当前节点无效
                if(stat.getMtime()<(date.getTime() - (conf.getBeforeTime()*1000)) && stat.getNumChildren() == 0){
                    // 只删除空目录
                    client.delete().forPath(projectDir + currPath);
                    logger.info("删除路径: " + projectDir + currPath);
                }
            }catch (Exception e){
                logger.error("删除节点失败: ", e);
            }
        });

    }
}
