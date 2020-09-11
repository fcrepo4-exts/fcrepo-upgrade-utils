/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fcrepo.upgrade.utils.f6;

import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author pwinckles
 */
public class MigrationTaskManager {

    private static final Logger LOGGER = getLogger(MigrationTaskManager.class);

    private final ExecutorService executorService;
    private final ResourceMigrator resourceMigrator;
    private final AtomicLong count;
    private final Object lock;

    public MigrationTaskManager(final ExecutorService executorService,
                                final ResourceMigrator resourceMigrator) {
        this.executorService = executorService;
        this.resourceMigrator = resourceMigrator;
        this.count = new AtomicLong(0);
        this.lock = new Object();
    }

    public void submit(final ResourceInfo info) {
        final var task = new MigrateResourceTask(this, resourceMigrator, info);

        executorService.submit(() -> {
            try {
                task.run();
            } finally {
                count.decrementAndGet();
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        });

        count.incrementAndGet();
    }

    public void awaitCompletion() throws InterruptedException {
        if (count.get() == 0) {
            return;
        }

        synchronized (lock) {
            while (count.get() > 0) {
                lock.wait();
            }
        }
    }

    public void shutdown() throws InterruptedException {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                LOGGER.error("Failed to shutdown executor service cleanly after 1 minute of waiting");
                executorService.shutdownNow();
            }
        } finally {
            resourceMigrator.close();
        }
    }

}
