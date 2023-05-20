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
 *       Microsoft Corporation - Initial implementation
 *
 */

package org.eclipse.edc.sample.extension.watchdog;

import org.eclipse.edc.connector.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.spi.command.CommandHandler;
import org.eclipse.edc.spi.monitor.Monitor;

import java.time.Clock;
import java.time.Duration;

import static java.lang.String.format;
import static java.time.Instant.ofEpochMilli;

public class CheckTimeoutCommandHandler implements CommandHandler<CheckTransferProcessTimeoutCommand> {
    private final TransferProcessStore store;
    private final Monitor monitor;
    private final Clock clock;

    public CheckTimeoutCommandHandler(TransferProcessStore store, Clock clock, Monitor monitor) {
        this.store = store;
        this.clock = clock;
        this.monitor = monitor;
    }

    @Override
    public void handle(CheckTransferProcessTimeoutCommand command) {

        var states = store.nextForState(command.getTargetState().code(), command.getBatchSize());
        states.stream().filter(tp -> isExpired(tp.getStateTimestamp(), command.getMaxAge()))
                .forEach(tp -> {
                    monitor.info(format("will retire TP with id [%s] due to timeout", tp.getId()));

                    tp.transitionError("timeout by watchdog");
                    store.save(tp);
                });
    }

    @Override
    public Class<CheckTransferProcessTimeoutCommand> getType() {
        return CheckTransferProcessTimeoutCommand.class;
    }

    private boolean isExpired(long stateTimestamp, Duration maxAge) {
        return ofEpochMilli(stateTimestamp).isBefore(clock.instant().minus(maxAge));
    }

}
