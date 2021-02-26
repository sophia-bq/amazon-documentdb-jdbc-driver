/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package software.amazon.documentdb.jdbc.calcite.adapter;

import com.google.common.collect.ImmutableMap;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.SneakyThrows;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.documentdb.jdbc.DocumentDbConnectionProperties;
import software.amazon.documentdb.jdbc.metadata.DocumentDbCollectionMetadata;
import software.amazon.documentdb.jdbc.metadata.DocumentDbMetadataScanner;
import software.amazon.documentdb.jdbc.metadata.DocumentDbMetadataTable;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Provides a schema for DocumentDB
 */
public class DocumentDbSchema extends AbstractSchema {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentDbSchema.class);
    private final MongoDatabase mongoDatabase;
    private final DocumentDbConnectionProperties properties;
    private ImmutableMap<String, Table> tables;

    protected DocumentDbSchema(final MongoDatabase mongoDatabase,
            final DocumentDbConnectionProperties properties) {
        this.mongoDatabase = mongoDatabase;
        this.properties = properties;
        this.tables = null;
    }

    public MongoDatabase getMongoDatabase() {
        return mongoDatabase;
    }

    @SneakyThrows
    @Override
    protected Map<String, Table> getTableMap() {
        if (tables == null) {
            final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder();
            for (String collectionName : mongoDatabase.listCollectionNames()) {
                final MongoCollection<BsonDocument> mongoCollection = mongoDatabase
                        .getCollection(collectionName, BsonDocument.class);
                final Iterator<BsonDocument> cursor = DocumentDbMetadataScanner
                        .getIterator(properties, mongoCollection);

                // Get the schema metadata.
                final DocumentDbCollectionMetadata metadata = DocumentDbCollectionMetadata
                        .create(collectionName, cursor);
                for (Entry<String, DocumentDbMetadataTable> entry : metadata.getTables().entrySet()) {
                    final DocumentDbMetadataTable metadataTable = entry.getValue();
                    builder.put(metadataTable.getName(), new DocumentDbTable(
                            collectionName, metadataTable));
                }
            }

            tables = builder.build();
        }

        return tables;
    }
}
