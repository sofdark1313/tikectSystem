package com.tikectsystem.service.kafka;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.core.SpringUtil;
import com.tikectsystem.dto.OrderRequestRecoverDto;
import com.tikectsystem.service.ProgramOrderService;
import com.tikectsystem.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Redis 故障恢复时，按时间窗口回扫 order_request Kafka。
 */
@Slf4j
@Service
public class OrderRequestRecoveryService {

    private static final int DEFAULT_SAFETY_ROLLBACK_SECONDS = 60;

    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);

    @Value("${spring.kafka.bootstrap-servers:127.0.0.1:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:program_order_request_data}")
    private String mainConsumerGroupId;

    @Autowired
    private OrderKafkaTopicProperties orderKafkaTopicProperties;

    @Autowired
    private ProgramOrderService programOrderService;

    /**
     * 回扫故障窗口内主消费组已 ACK 的 order_request，并补发缺失的 order_create。
     */
    public int recover(OrderRequestRecoverDto recoverDto) {
        Date faultTime = recoverDto.getFaultTime();
        int rollbackSeconds = recoverDto.getSafetyRollbackSeconds() == null ?
                DEFAULT_SAFETY_ROLLBACK_SECONDS : recoverDto.getSafetyRollbackSeconds();
        Date startTime = DateUtils.addSecond(faultTime, -rollbackSeconds);
        Date endTime = recoverDto.getEndTime() == null ? DateUtils.now() : recoverDto.getEndTime();
        boolean onlyCommitted = !Objects.equals(recoverDto.getOnlyCommittedRequest(), Boolean.FALSE);
        String topic = SpringUtil.getPrefixDistinctionName() + "-" + orderKafkaTopicProperties.getOrderRequestTopic();

        Properties properties = buildConsumerProperties();
        int recoveredCount = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            List<TopicPartition> topicPartitions = getTopicPartitions(consumer, topic);
            if (topicPartitions.isEmpty()) {
                return 0;
            }
            consumer.assign(topicPartitions);
            Map<TopicPartition, Long> endOffsetMap = seekToStartAndGetEndOffsets(consumer, topicPartitions, startTime, endTime);
            Map<TopicPartition, Long> committedOffsetMap = onlyCommitted ?
                    getCommittedOffsetMap(topicPartitions) : Map.of();

            while (hasMoreRecords(consumer, endOffsetMap)) {
                for (ConsumerRecord<String, String> record : consumer.poll(POLL_TIMEOUT)) {
                    if (record.timestamp() < startTime.getTime() || record.timestamp() > endTime.getTime()) {
                        continue;
                    }
                    TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
                    if (onlyCommitted && record.offset() >= committedOffsetMap.getOrDefault(topicPartition, 0L)) {
                        continue;
                    }
                    if (recoverRecord(record, recoverDto.getProgramId())) {
                        recoveredCount++;
                    }
                }
            }
        }
        return recoveredCount;
    }

    private Properties buildConsumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "order-request-recovery-" + UUID.randomUUID());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }

    private List<TopicPartition> getTopicPartitions(KafkaConsumer<String, String> consumer, String topic) {
        List<PartitionInfo> partitionInfoList = consumer.partitionsFor(topic);
        List<TopicPartition> topicPartitions = new ArrayList<>();
        if (partitionInfoList == null) {
            return topicPartitions;
        }
        for (PartitionInfo partitionInfo : partitionInfoList) {
            topicPartitions.add(new TopicPartition(topic, partitionInfo.partition()));
        }
        return topicPartitions;
    }

    private Map<TopicPartition, Long> seekToStartAndGetEndOffsets(KafkaConsumer<String, String> consumer,
                                                                  List<TopicPartition> topicPartitions,
                                                                  Date startTime,
                                                                  Date endTime) {
        Map<TopicPartition, Long> latestEndOffsetMap = consumer.endOffsets(topicPartitions);
        Map<TopicPartition, Long> startTimestampMap = new HashMap<>(topicPartitions.size());
        Map<TopicPartition, Long> endTimestampMap = new HashMap<>(topicPartitions.size());
        for (TopicPartition topicPartition : topicPartitions) {
            startTimestampMap.put(topicPartition, startTime.getTime());
            endTimestampMap.put(topicPartition, endTime.getTime());
        }
        Map<TopicPartition, OffsetAndTimestamp> startOffsetMap = consumer.offsetsForTimes(startTimestampMap);
        Map<TopicPartition, OffsetAndTimestamp> endOffsetByTimeMap = consumer.offsetsForTimes(endTimestampMap);
        Map<TopicPartition, Long> endOffsetMap = new HashMap<>(topicPartitions.size());
        for (TopicPartition topicPartition : topicPartitions) {
            OffsetAndTimestamp startOffset = startOffsetMap.get(topicPartition);
            consumer.seek(topicPartition, startOffset == null ? latestEndOffsetMap.get(topicPartition) : startOffset.offset());

            OffsetAndTimestamp endOffset = endOffsetByTimeMap.get(topicPartition);
            endOffsetMap.put(topicPartition, endOffset == null ? latestEndOffsetMap.get(topicPartition) : endOffset.offset());
        }
        return endOffsetMap;
    }

    private Map<TopicPartition, Long> getCommittedOffsetMap(List<TopicPartition> topicPartitions) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(properties)) {
            ListConsumerGroupOffsetsResult result = adminClient.listConsumerGroupOffsets(mainConsumerGroupId);
            Map<TopicPartition, OffsetAndMetadata> offsetAndMetadataMap = result.partitionsToOffsetAndMetadata().get();
            Map<TopicPartition, Long> committedOffsetMap = new HashMap<>(topicPartitions.size());
            for (TopicPartition topicPartition : topicPartitions) {
                OffsetAndMetadata offsetAndMetadata = offsetAndMetadataMap.get(topicPartition);
                committedOffsetMap.put(topicPartition, offsetAndMetadata == null ? 0L : offsetAndMetadata.offset());
            }
            return committedOffsetMap;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("query order_request committed offset interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("query order_request committed offset failed", e);
        }
    }

    private boolean hasMoreRecords(KafkaConsumer<String, String> consumer, Map<TopicPartition, Long> endOffsetMap) {
        for (Map.Entry<TopicPartition, Long> entry : endOffsetMap.entrySet()) {
            if (consumer.position(entry.getKey()) < entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    private boolean recoverRecord(ConsumerRecord<String, String> record, Long programId) {
        try {
            OrderRequestMq orderRequestMq = JSON.parseObject(record.value(), OrderRequestMq.class);
            if (orderRequestMq == null || orderRequestMq.getProgramOrderCreateDto() == null ||
                    orderRequestMq.getOrderNumber() == null) {
                return false;
            }
            if (programId != null && !Objects.equals(programId, orderRequestMq.getProgramOrderCreateDto().getProgramId())) {
                return false;
            }
            return programOrderService.recoverOrderRequest(orderRequestMq);
        } catch (Exception e) {
            log.error("recover order_request failed, topic:{}, partition:{}, offset:{}, value:{}",
                    record.topic(), record.partition(), record.offset(), record.value(), e);
            return false;
        }
    }
}
