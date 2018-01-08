import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.UnsupportedEncodingException;

public class Reproducer {
    public static void main(String[] args) {


        String broker = "localhost";
        int port = 1884;
        int qos = 0;
        String topic = "my_app/report/new";

        //-------------------------- Vertx MQTT client configuration ---------------//
        Vertx vertx = Vertx.vertx();
        io.vertx.mqtt.MqttClientOptions options = new MqttClientOptions();
        options.setMaxMessageSize(100_000_000);
        options.setCleanSession(false);
        io.vertx.mqtt.MqttClient vertxMqttClient = MqttClient.create(vertx, options);


        //-------------------------- Paho client configuration ---------------------//
        org.eclipse.paho.client.mqttv3.MqttClient pahoMqttClient = null;
        MqttConnectOptions pahoMqttConnectOptions = new MqttConnectOptions();
        try {
            pahoMqttClient = new org.eclipse.paho.client.mqttv3.MqttClient("tcp://" + broker + ":" + port, "234", new MemoryPersistence());
        } catch (MqttException e) {
            e.printStackTrace();
        }

        // --------------------------- VERTX --------------------------------------//
        // connect -> subscribe to "my_app/report/#"
        vertxMqttClient.subscribeCompletionHandler(h -> {
            System.out.println("[VERTX] Subscribe complete, levels" + h.grantedQoSLevels());
        });

        vertxMqttClient.publishHandler(s -> {
            try {
                String message = new String(s.payload().getBytes(), "UTF-8");
                System.out.println(String.format("[VERTX] Receive message with content: \"%s\" from topic \"%s\"", message, s.topicName()));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        });
        vertxMqttClient.connect(port, broker, s -> {
            // subscribe to all subtopics
            vertxMqttClient.subscribe("my_app/report/#", qos);
        });

        // --------------------------- PAHO --------------------------------------//
        // connect -> subscribe to "my_app/report/#"
        try {
            pahoMqttClient.connect(pahoMqttConnectOptions);
            pahoMqttClient.subscribe("my_app/report/#", qos, (topicWithUpdates, message) -> {
                System.out.println(String.format("[PAHO] Receive message with content: \"%s\" from topic \"%s\"", message, topicWithUpdates));
            });
            pahoMqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) { }

                @Override
                public void messageArrived(String topicWithUpdates, MqttMessage message) throws Exception {
                    System.out.println(String.format("[PAHO] Receive message with content: \"%s\" from topic \"%s\"", message, topicWithUpdates));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) { }
            });

            // wait for subscription completion and connection establishment
            Thread.sleep(1000);

            // both paho and vertx mqtt client send 2 messages
            for (int i = 0; i < 2; i++) {
                MqttMessage message1 = new MqttMessage("Hello From Paho(**): multi level my_app message".getBytes());
                message1.setQos(qos);
                pahoMqttClient.publish(topic, message1);
                vertxMqttClient.publish(topic, Buffer.buffer("Hello from Vertx(++): multi level my_app message"), MqttQoS.valueOf(qos), false, false);
            }

            // wait for some time ... clients should receive messages
            Thread.sleep(5000);
            System.exit(0);
        } catch (MqttException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
