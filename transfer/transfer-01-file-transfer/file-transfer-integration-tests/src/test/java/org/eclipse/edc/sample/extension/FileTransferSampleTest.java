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


import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.EdcRuntimeExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.eclipse.edc.sample.extension.FileTransferSampleTestCommon.getFileFromRelativePath;

@EndToEndTest
public class FileTransferSampleTest {

    static final String CONSUMER_CONFIG_PROPERTIES_FILE_PATH = "transfer/transfer-01-file-transfer/file-transfer-consumer/config.properties";
    static final String PROVIDER_CONFIG_PROPERTIES_FILE_PATH = "transfer/transfer-01-file-transfer/file-transfer-provider/config.properties";
    static final String SAMPLE_ASSET_FILE_PATH = "transfer/transfer-01-file-transfer/README.md";
    static final String DESTINATION_FILE_PATH = "transfer/transfer-01-file-transfer/requested.test.txt";

    @RegisterExtension
    static EdcRuntimeExtension provider = new EdcRuntimeExtension(
            ":transfer:transfer-01-file-transfer:file-transfer-provider",
            "provider",
            Map.of(
                    // Override 'edc.samples.transfer.01.asset.path' implicitly set via property 'edc.fs.config'.
                    "edc.samples.transfer.01.asset.path", getFileFromRelativePath(SAMPLE_ASSET_FILE_PATH).getAbsolutePath(),
                    "edc.fs.config", getFileFromRelativePath(PROVIDER_CONFIG_PROPERTIES_FILE_PATH).getAbsolutePath()
            )
    );
    @RegisterExtension
    static EdcRuntimeExtension consumer = new EdcRuntimeExtension(
            ":transfer:transfer-01-file-transfer:file-transfer-consumer",
            "consumer",
            Map.of(
                    "edc.fs.config", getFileFromRelativePath(CONSUMER_CONFIG_PROPERTIES_FILE_PATH).getAbsolutePath()
            )
    );
    final FileTransferSampleTestCommon testUtils = new FileTransferSampleTestCommon(SAMPLE_ASSET_FILE_PATH, DESTINATION_FILE_PATH);

    /**
     * Run all sample steps in one single test.
     * Note: Sample steps cannot be separated into single tests because {@link EdcRuntimeExtension}
     * runs before each single test.
     */
    @Test
    void runSampleSteps() throws Exception {
        testUtils.assertTestPrerequisites();

        testUtils.initiateContractNegotiation();
        testUtils.lookUpContractAgreementId();
        var transferProcessId = testUtils.requestTransferFile();
        testUtils.assertDestinationFileContent();

        testUtils.assertTransferProcessStatusConsumerSide(transferProcessId);
    }

    @AfterEach
    protected void tearDown() {
        testUtils.cleanTemporaryTestFiles();
    }
}
