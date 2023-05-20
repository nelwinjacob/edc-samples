# Implement a simple transfer listener

In this sample, we build upon the [file transfer sample](../transfer-01-file-transfer/README.md) to add functionality
to react to transfer completion on the consumer connector side.

We will use the provider from the [file transfer sample](../transfer-01-file-transfer/README.md), and the consumer
built on the consumer from that sample, with a transfer process listener added.

Also, in order to keep things organized, the code in this example has been separated into several Java modules:

- `file-transfer-listener-consumer`: this is where the extension definition and dependencies reside for the consumer connector
- `listener`: contains the `TransferProcessListener` implementation

## Create the listener

A TransferProcessListener may define methods that are invoked after a transfer changes state, for example, to notify an
external application on the consumer side after data has been produced (i.e. the transfer moves to the completed state).

```java
// in TransferListenerExtension.java
    @Override
    public void initialize(ServiceExtensionContext context) {
        // ...
        var transferProcessObservable = context.getService(TransferProcessObservable.class);
        transferProcessObservable.registerListener(new MarkerFileCreator(monitor));
    }
```

```java
public class MarkerFileCreator implements TransferProcessListener {

    /**
     * Callback invoked by the EDC framework when a transfer has completed.
     *
     * @param process
     */
    @Override
    public void completed(final TransferProcess process) {
        // ...
    }
}
```

## Perform a file transfer

Let's rebuild and run them both:

```bash
./gradlew transfer:transfer-02-file-transfer-listener:file-transfer-listener-consumer:build
java -Dedc.fs.config=transfer/transfer-02-file-transfer-listener/file-transfer-listener-consumer/config.properties -jar transfer/transfer-02-file-transfer-listener/file-transfer-listener-consumer/build/libs/consumer.jar
# in another terminal window:
./gradlew transfer:transfer-01-file-transfer:file-transfer-provider:build
java -Dedc.fs.config=transfer/transfer-01-file-transfer/file-transfer-provider/config.properties -jar transfer/transfer-01-file-transfer/file-transfer-provider/build/libs/provider.jar
````

Assuming you didn't change the config files, the consumer will expose management api on port `9192` and the custom 
api endpoints on port `9191` and the provider will listen on port `8181`.
Open another terminal window (or any REST client of your choice) and execute the following REST requests like in the
previous sample:

```bash
curl -X POST -H "Content-Type: application/json" -H "X-Api-Key: password" -d @transfer/transfer-01-file-transfer/contractoffer.json "http://localhost:9192/api/v1/management/contractnegotiations"
curl -X GET -H 'X-Api-Key: password' "http://localhost:9192/api/v1/management/contractnegotiations/{UUID}"
curl -X POST -H "Content-Type: application/json" -H "X-Api-Key: password" -d @transfer/transfer-01-file-transfer/filetransfer.json "http://localhost:9192/api/v1/management/transferprocess"
```

> **Replace `{UUID}` in the second request with the UUID received as the response to the first request!**
>
> **In `transfer/transfer-01-file-transfer/filetransfer.json`: Copy the contract agreement's ID from the second response,
> substitute it for `{agreement ID}` in the last request JSON body and adjust the `dataDestination.properties.path` to match your local dev machine!**

- `curl` will return the ID of the transfer process on the consumer connector.

The consumer should spew out logs similar to:

```bash
DEBUG 2022-04-14T16:23:13.4042547 Starting transfer for asset test-document
DEBUG 2022-04-14T16:23:13.4072776 Transfer process initialised 6804ed96-298e-4992-b72d-2366d97cf7a6
DEBUG 2022-04-14T16:23:13.8801678 TransferProcessManager: Sending process 6804ed96-298e-4992-b72d-2366d97cf7a6 request to http://localhost:8282/api/v1/ids/data
DEBUG 2022-04-14T16:23:13.9341802 TransferProcessManager: Process 6804ed96-298e-4992-b72d-2366d97cf7a6 is now REQUESTED
DEBUG 2022-04-14T16:23:18.9048494 Process 6804ed96-298e-4992-b72d-2366d97cf7a6 is now IN_PROGRESS
DEBUG 2022-04-14T16:23:18.9048494 Process 6804ed96-298e-4992-b72d-2366d97cf7a6 is now COMPLETED
INFO 2022-04-14T16:23:18.9048494 Transfer Listener successfully wrote file C:\Users\pechande\dev\coding\EDC\marker.txt
```

Then check `/path/on/yourmachine`, which should now contain a file named `marker.txt` in addition to the file defined
in `dataDestination.properties.path` in `transfer/transfer-01-file-transfer/filetransfer.json`.

---

[Previous Chapter](../transfer-01-file-transfer/README.md) | [Next Chapter](../transfer-03-modify-transferprocess/README.md)
