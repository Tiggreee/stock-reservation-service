package lat.vmdev.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic stockMovementsTopic(InventoryProperties props) {
        return TopicBuilder.name(props.kafka().topics().stockMovements())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic inventoryEventsTopic(InventoryProperties props) {
        return TopicBuilder.name(props.kafka().topics().inventoryEvents())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    KafkaTemplate<String, Object> objectKafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
