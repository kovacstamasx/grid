package hu.budapest;

import com.beanit.openiec61850.BasicDataAttribute;
import com.beanit.openiec61850.BdaFloat32;
import com.beanit.openiec61850.SclParseException;
import com.beanit.openiec61850.ServerEventListener;
import com.beanit.openiec61850.ServerModel;
import com.beanit.openiec61850.ServerSap;
import com.beanit.openiec61850.ServiceError;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.beanit.openiec61850.Fc.MX;
import static com.beanit.openiec61850.SclParser.parse;

@Slf4j
public class SubstationServer {

    private static ServerModel model;

    public static void main(String[] args) {
        try {
            // Parse server model from descriptor
            List<ServerModel> serverModels;
            try (InputStream inputStream = SubstationServer.class.getClassLoader().getResourceAsStream("sample-model.icd")) {
                serverModels = parse(inputStream);
            } catch (SclParseException e) {
                log.error("Can't parse server model descriptor", e);
                throw new RuntimeException(e);
            }
            assert serverModels != null && serverModels.size() == 1;

            // Start the server
            log.info("Starting Substation Simulation...");
            var serverSap = new ServerSap(4000, 102, null, serverModels.getFirst(), null);
            model = serverSap.getModelCopy();
            serverSap.startListening(new ServerEventListener() {
                @Override
                public List<ServiceError> write(List<BasicDataAttribute> bdas) {
                    log.info("write event listener called");
                    return List.of();
                }

                @Override
                public void serverStoppedListening(ServerSap serverSAP) {
                    log.info("serverStoppedListening event listener called");
                }
            });
            log.info("Simulation running. Press Ctrl+C to stop.");

            // Periodically update power data to simulate real-time changes
            var timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    // Simulate a random fluctuation in total power
                    var newPower = 10000.0f + (float) (Math.random() * 10 - 5); // ±5W fluctuation
                    var node = model.findModelNode("ied1lDevice1/MMXU1.TotW.mag.f", MX);

                    if (node instanceof BdaFloat32 bda) {
                        bda.setFloat(newPower);

                        serverSap.setValues(List.of(bda));
                        log.info("Update power: {}W", newPower);
                    }
                }
            }, 0, 2000); // Update every 2 seconds
        } catch (IOException e) {
            log.error("Error starting server: {}", e.getMessage());
        }
    }
}