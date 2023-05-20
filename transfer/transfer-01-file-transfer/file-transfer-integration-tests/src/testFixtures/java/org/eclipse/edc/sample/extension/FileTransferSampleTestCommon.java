/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial test implementation for sample
 *
 */

package org.eclipse.edc.sample.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.apache.http.HttpStatus;
import org.eclipse.edc.connector.api.management.transferprocess.model.TransferProcessDto;
import org.eclipse.edc.connector.transfer.spi.types.DataRequest;
import org.eclipse.edc.connector.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Encapsulates common settings, test steps, and helper methods for the test for samples 4.x.
 */
public class FileTransferSampleTestCommon {

    static final ObjectMapper MAPPER = new ObjectMapper();

    //region constant test settings
    static final String INITIATE_CONTRACT_NEGOTIATION_URI = "http://localhost:9192/api/v1/management/contractnegotiations";
    static final String LOOK_UP_CONTRACT_AGREEMENT_URI = "http://localhost:9192/api/v1/management/contractnegotiations/{id}";
    static final String TRANSFER_PROCESS_URI = "http://localhost:9192/api/v1/management/transferprocess";
    static final String CONTRACT_OFFER_FILE_PATH = "transfer/transfer-01-file-transfer/contractoffer.json";
    static final String TRANSFER_FILE_PATH = "transfer/transfer-01-file-transfer/filetransfer.json";
    static final String API_KEY_HEADER_KEY = "X-Api-Key";
    static final String API_KEY_HEADER_VALUE = "password";
    //endregion

    //region changeable test settings
    final String sampleAssetFilePath;
    final File sampleAssetFile;
    final String destinationFilePath;
    final File destinationFile;
    Duration timeout = Duration.ofSeconds(15);
    Duration pollInterval = Duration.ofMillis(500);
    //endregion

    String contractNegotiationId;
    String contractAgreementId;

    /**
     * Creates a new {@code FileTransferSampleTestCommon} instance.
     *
     * @param sampleAssetFilePath Relative path starting from the root of the project to a file which will be read from for transfer.
     * @param destinationFilePath Relative path starting from the root of the project where the transferred file will be written to.
     */
    public FileTransferSampleTestCommon(@NotNull String sampleAssetFilePath, @NotNull String destinationFilePath) {
        this.sampleAssetFilePath = sampleAssetFilePath;
        sampleAssetFile = getFileFromRelativePath(sampleAssetFilePath);

        this.destinationFilePath = sampleAssetFilePath;
        destinationFile = getFileFromRelativePath(destinationFilePath);
    }

    /**
     * Resolves a {@link File} instance from a relative path.
     */
    @NotNull
    public static File getFileFromRelativePath(String relativePath) {
        return new File(TestUtils.findBuildRoot(), relativePath);
    }

    /**
     * Assert that prerequisites are fulfilled before running the test.
     * This assertion checks only whether the file to be copied is not existing already.
     */
    void assertTestPrerequisites() {
        assertThat(destinationFile).doesNotExist();
    }

    /**
     * Remove files created while running the tests.
     * The copied file will be deleted.
     */
    void cleanTemporaryTestFiles() {
        destinationFile.delete();
    }

    /**
     * Assert that the file to be copied exists at the expected location.
     * This method waits a duration which is defined in {@link FileTransferSampleTestCommon#timeout}.
     */
    void assertDestinationFileContent() {
        await().atMost(timeout).pollInterval(pollInterval).untilAsserted(()
                -> assertThat(destinationFile).hasSameBinaryContentAs(sampleAssetFile));
    }

    /**
     * Assert that the transfer process state on the consumer is completed.
     */
    void assertTransferProcessStatusConsumerSide(String transferProcessId) {
        await().atMost(timeout).pollInterval(pollInterval).untilAsserted(()
                -> {
            var transferProcess = getTransferProcessById(transferProcessId);

            assertThat(transferProcess).extracting(TransferProcessDto::getState).isEqualTo(TransferProcessStates.COMPLETED.toString());
        });
    }

    /**
     * Assert that a POST request to initiate a contract negotiation is successful.
     * This method corresponds to the command in the sample: {@code curl -X POST -H "Content-Type: application/json" -H "X-Api-Key: password" -d @transfer/transfer-01-file-transfer/contractoffer.json "http://localhost:9192/api/v1/management/contractnegotiations"}
     */
    void initiateContractNegotiation() {
        contractNegotiationId = RestAssured
                .given()
                .headers(API_KEY_HEADER_KEY, API_KEY_HEADER_VALUE)
                .contentType(ContentType.JSON)
                .body(new File(TestUtils.findBuildRoot(), CONTRACT_OFFER_FILE_PATH))
                .when()
                .post(INITIATE_CONTRACT_NEGOTIATION_URI)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", not(emptyString()))
                .extract()
                .jsonPath()
                .get("id");
    }

    /**
     * Gets the first transfer process as returned by the management API.
     *
     * @return The transfer process.
     */
    public TransferProcessDto getTransferProcess() {
        return RestAssured.given()
                .headers(API_KEY_HEADER_KEY, API_KEY_HEADER_VALUE)
                .when()
                .get(TRANSFER_PROCESS_URI)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().jsonPath().getObject("[0]", TransferProcessDto.class);
    }

    /**
     * Gets the transfer process by ID.
     *
     * @return The transfer process.
     */
    public TransferProcessDto getTransferProcessById(String processId) {
        return RestAssured.given()
                .headers(API_KEY_HEADER_KEY, API_KEY_HEADER_VALUE)
                .when()
                .get(String.format("%s/%s", TRANSFER_PROCESS_URI, processId))
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().body().as(TransferProcessDto.class);
    }

    /**
     * Assert that a GET request to look up a contract agreement is successful.
     * This method corresponds to the command in the sample: {@code curl -X GET -H 'X-Api-Key: password' "http://localhost:9192/api/v1/management/contractnegotiations/{UUID}"}
     */
    void lookUpContractAgreementId() {
        // Wait for transfer to be completed.
        await().atMost(timeout).pollInterval(pollInterval).untilAsserted(() -> contractAgreementId = RestAssured
                .given()
                .headers(API_KEY_HEADER_KEY, API_KEY_HEADER_VALUE)
                .when()
                .get(LOOK_UP_CONTRACT_AGREEMENT_URI, contractNegotiationId)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("state", equalTo("CONFIRMED"))
                .body("contractAgreementId", not(emptyString()))
                .extract().body().jsonPath().getString("contractAgreementId")
        );
    }

    /**
     * Assert that a POST request to initiate transfer process is successful.
     * This method corresponds to the command in the sample: {@code curl -X POST -H "Content-Type: application/json" -H "X-Api-Key: password" -d @transfer/transfer-01-file-transfer/filetransfer.json "http://localhost:9192/api/v1/management/transferprocess"}
     *
     * @throws IOException Thrown if there was an error accessing the transfer request file defined in {@link FileTransferSampleTestCommon#TRANSFER_FILE_PATH}.
     */
    String requestTransferFile() throws IOException {
        var transferJsonFile = getFileFromRelativePath(TRANSFER_FILE_PATH);
        DataRequest sampleDataRequest = readAndUpdateDataRequestFromJsonFile(transferJsonFile, contractAgreementId);

        JsonPath jsonPath = RestAssured
                .given()
                .headers(API_KEY_HEADER_KEY, API_KEY_HEADER_VALUE)
                .contentType(ContentType.JSON)
                .body(sampleDataRequest)
                .when()
                .post(TRANSFER_PROCESS_URI)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("id", not(emptyString()))
                .extract()
                .jsonPath();

        String transferProcessId = jsonPath.get("id");

        assertThat(transferProcessId).isNotEmpty();

        return transferProcessId;
    }

    /**
     * Reads a transfer request file with changed value for contract agreement ID and file destination path.
     *
     * @param transferJsonFile A {@link File} instance pointing to a JSON transfer request file.
     * @param contractAgreementId This string containing a UUID will be used as value for the contract agreement ID.
     * @return An instance of {@link DataRequest} with changed values for contract agreement ID and file destination path.
     * @throws IOException Thrown if there was an error accessing the file given in transferJsonFile.
     */
    DataRequest readAndUpdateDataRequestFromJsonFile(@NotNull File transferJsonFile, @NotNull String contractAgreementId) throws IOException {
        // convert JSON file to map
        DataRequest sampleDataRequest = MAPPER.readValue(transferJsonFile, DataRequest.class);

        var changedAddressProperties = sampleDataRequest.getDataDestination().getProperties();
        changedAddressProperties.put("path", destinationFile.getAbsolutePath());

        DataAddress newDataDestination = DataAddress.Builder.newInstance()
                .properties(changedAddressProperties)
                .build();

        return DataRequest.Builder.newInstance()
                // copy unchanged values from JSON file
                .id(sampleDataRequest.getId())
                .processId(sampleDataRequest.getProcessId())
                .connectorAddress(sampleDataRequest.getConnectorAddress())
                .protocol(sampleDataRequest.getProtocol())
                .connectorId(sampleDataRequest.getConnectorId())
                .assetId(sampleDataRequest.getAssetId())
                .destinationType(sampleDataRequest.getDestinationType())
                .transferType(sampleDataRequest.getTransferType())
                .managedResources(sampleDataRequest.isManagedResources())
                .properties(sampleDataRequest.getProperties())
                // set changed values
                .contractId(contractAgreementId)
                .dataDestination(newDataDestination)
                .build();
    }
}
