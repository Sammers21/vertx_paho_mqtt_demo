# A java demo of Vertx Mqtt client and Paho Mqtt client

Steps to run this demo:
* 1st - Download and run _vertx-mqtt-broker_: 
```bash 
git clone https://github.com/GruppoFilippetti/vertx-mqtt-broker.git
cd vertx-mqtt-broker
mvn clean install
java -jar target/vertx-mqtt-broker-2.2.12-fat.jar -c config_cluster1.json
```

* 2nd - run this demo(in separated console):
```bash
git clone https://github.com/Sammers21/vertx_paho_mqtt_demo
cd vertx_paho_mqtt_demo
mvn clean package
java -jar java -jar target/reproducer-1-all-in-one.jar 
```