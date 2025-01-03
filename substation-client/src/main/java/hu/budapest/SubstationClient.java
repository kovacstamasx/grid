package hu.budapest;

import com.beanit.openiec61850.BdaFloat32;
import com.beanit.openiec61850.ClientEventListener;
import com.beanit.openiec61850.ClientSap;
import com.beanit.openiec61850.Report;
import com.beanit.openiec61850.Urcb;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.beanit.openiec61850.Fc.RP;
import static java.lang.Long.MAX_VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class SubstationClient {

    private static final String BROKER_URL = "tcp://mosquitto:1883"; // Mosquitto broker URL
    private static final String CLIENT_ID = "SubStationClient";

    public static void main(String[] args) throws UnknownHostException {
        var serverIp = InetAddress.getByName("substation-server");
        var serverPort = 4000;

        // Create an MQTT client
        try (var mqttClient = new MqttClient(BROKER_URL, CLIENT_ID)) {
            // Connect to the broker
            var options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            mqttClient.connect(options);
            log.info("Connected to Mosquitto broker at " + BROKER_URL);

            // Connect to IEC 61850 server
            var clientSap = new ClientSap();
            var client = clientSap.associate(serverIp, serverPort, null, new ClientEventListener() {
                @Override
                public void newReport(Report report) {
                    var node = report.getValues().getFirst().getChild("mag").getChild("f");
                    if (node instanceof BdaFloat32 value) {
                        try {
                            // Publish a message
                            var mqttMessage = new MqttMessage(value.getValueString().getBytes(UTF_8));
                            mqttMessage.setQos(1);  // Quality of Service level 1
                            mqttClient.publish("substation/metric", mqttMessage);
                            log.info("New value published: {}", value.getValueString());
                        } catch (MqttException e) {
                            log.error("Can't publish message", e);
                            throw new RuntimeException(e);
                        }
                    }
                }

                @Override
                public void associationClosed(IOException e) {

                }
            });

            // Subscribe for data update
            var node = client.retrieveModel().findModelNode("ied1lDevice1/LLN0.urcb101", RP);
            if (node instanceof Urcb urcb) {
                client.enableReporting(urcb);
            }

            log.info("Bridge running...");
            Thread.sleep(MAX_VALUE);
        } catch (Exception e) {
            log.error("Can't run substation client.", e);
        }
    }
}