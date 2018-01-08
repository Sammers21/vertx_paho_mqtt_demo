import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
        // connect -> subscribe to "my_app/report/#" -> publishing "Hello from Vertx(++): multi level my_app message" messages a lot of times
        vertxMqttClient.subscribeCompletionHandler(h -> {
            System.out.println("[VERTX] Subscribe complete, levels" + h.grantedQoSLevels());
            vertxMqttClient.publish("my_app/report/new", Buffer.buffer("Hello from Vertx(++): multi level my_app message"), MqttQoS.valueOf(qos), false, false);
        });

        vertxMqttClient.publishHandler(s -> {
            try {
                Thread.sleep(1000);
                String message = new String(s.payload().getBytes(), "UTF-8");
                System.out.println(String.format("[VERTX] Receive message with content: \"%s\" from topic \"%s\"", message, s.topicName()));
                vertxMqttClient.publish(topic, Buffer.buffer("Hello from Vertx(++): multi level my_app message"), MqttQoS.valueOf(qos), false, false);
            } catch (UnsupportedEncodingException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        vertxMqttClient.connect(port, broker, s -> {
            // subscribe to all subtopics
            vertxMqttClient.subscribe("my_app/report/#", qos);
        });

        // --------------------------- PAHO --------------------------------------//
        // connect -> subscribe to "my_app/report/#" -> publishing "Hello From Paho(**): multi level my_app message" messages a lot of times

        try {
            pahoMqttClient.connect(pahoMqttConnectOptions);
            final org.eclipse.paho.client.mqttv3.MqttClient finalPahoMqttClient = pahoMqttClient;
            pahoMqttClient.subscribe("my_app/report/#", qos, (topicWithUpdates, message) -> {
                Thread.sleep(1000);
                System.out.println(String.format("[PAHO] Receive message with content: \"%s\" from topic \"%s\"", message, topicWithUpdates));
            });
            pahoMqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {

                }

                @Override
                public void messageArrived(String topicWithUpdates, MqttMessage message) throws Exception {
                    System.out.println(String.format("[PAHO] Receive message with content: \"%s\" from topic \"%s\"", message, topicWithUpdates));
                    MqttMessage message1 = new MqttMessage("Hello From Paho(**): multi level my_app message".getBytes());
                    message1.setQos(qos);
                    finalPahoMqttClient.publish(topic, message1);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
