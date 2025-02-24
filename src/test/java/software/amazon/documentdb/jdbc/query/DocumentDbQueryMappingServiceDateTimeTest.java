/*
 * Copyright <2021> Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package software.amazon.documentdb.jdbc.query;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.documentdb.jdbc.calcite.adapter.DocumentDbFilter;
import software.amazon.documentdb.jdbc.common.test.DocumentDbFlapDoodleExtension;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@ExtendWith(DocumentDbFlapDoodleExtension.class)
public class DocumentDbQueryMappingServiceDateTimeTest extends DocumentDbQueryMappingServiceTest {
    private static final String DATE_COLLECTION_NAME = "dateTestCollection";
    private static DocumentDbQueryMappingService queryMapper;

    @BeforeAll
    void initialize() throws SQLException {
        final long dateTime = Instant.parse("2020-01-01T00:00:00.00Z").toEpochMilli();
        final BsonDocument document = BsonDocument.parse("{\"_id\": 101}");
        document.append("field", new BsonDateTime(dateTime));
        insertBsonDocuments(DATE_COLLECTION_NAME, new BsonDocument[]{document});
        queryMapper = getQueryMappingService();
    }

    /**
     * Tests TIMESTAMPADD() and EXTRACT().
     * @throws SQLException occurs if query fails.
     */
    @Test
    @DisplayName("Tests TIMESTAMPADD() and EXTRACT().")
    void testDateFunctions() throws SQLException {
        final String timestampAddQuery =
                String.format(
                        "SELECT "
                                + "TIMESTAMPADD(WEEK, 1, \"field\"), "
                                + "TIMESTAMPADD(DAY, 2, \"field\"), "
                                + "TIMESTAMPADD(HOUR, 3, \"field\"), "
                                + "TIMESTAMPADD(MINUTE, 4, \"field\"), "
                                + "TIMESTAMPADD(SECOND, 5, \"field\"), "
                                + "TIMESTAMPADD(MICROSECOND, 6, \"field\") "
                                + "FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        DocumentDbMqlQueryContext result = queryMapper.get(timestampAddQuery);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(DATE_COLLECTION_NAME, result.getCollectionName());
        Assertions.assertEquals(6, result.getColumnMetaData().size());
        Assertions.assertEquals(1, result.getAggregateOperations().size());
        Assertions.assertEquals(
                BsonDocument.parse(
                        "{\"$project\": {"
                                + "\"EXPR$0\": {\"$add\": [\"$field\", {\"$multiply\": [{\"$literal\": 604800000}, {\"$literal\": 1}]}]}, "
                                + "\"EXPR$1\": {\"$add\": [\"$field\", {\"$multiply\": [{\"$literal\": 86400000}, {\"$literal\": 2}]}]}, "
                                + "\"EXPR$2\": {\"$add\": [\"$field\", {\"$multiply\": [{\"$literal\": 3600000}, {\"$literal\": 3}]}]}, "
                                + "\"EXPR$3\": {\"$add\": [\"$field\", {\"$multiply\": [{\"$literal\": 60000}, {\"$literal\": 4}]}]}, "
                                + "\"EXPR$4\": {\"$add\": [\"$field\", {\"$multiply\": [{\"$literal\": 1000}, {\"$literal\": 5}]}]}, "
                                + "\"EXPR$5\": {\"$add\": [\"$field\", {\"$divide\": [{\"$subtract\": [{\"$multiply\": [{\"$literal\": 1}, {\"$literal\": 6}]}, {\"$mod\": [{\"$multiply\": [{\"$literal\": 1}, {\"$literal\": 6}]}, {\"$literal\": 1000}]}]}, {\"$literal\": 1000}]}]}, "
                                + "\"_id\": 0}}").toJson(),
                ((BsonDocument) result.getAggregateOperations().get(0)).toJson());

        final String extractQuery =
                String.format(
                        "SELECT "
                                + "YEAR(\"field\"), "
                                + "MONTH(\"field\"),"
                                + "WEEK(\"field\"),"
                                + "DAYOFMONTH(\"field\"),"
                                + "DAYOFWEEK(\"field\"),"
                                + "DAYOFYEAR(\"field\"),"
                                + "HOUR(\"field\"),"
                                + "MINUTE(\"field\"),"
                                + "SECOND(\"field\"),"
                                + "QUARTER(\"field\")"
                                + "FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        result = queryMapper.get(extractQuery);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(DATE_COLLECTION_NAME, result.getCollectionName());
        Assertions.assertEquals(10, result.getColumnMetaData().size());
        Assertions.assertEquals(1, result.getAggregateOperations().size());
        Assertions.assertEquals(
                BsonDocument.parse(
                        "{\"$project\":"
                                + " {\"EXPR$0\": {\"$year\": \"$field\"},"
                                + " \"EXPR$1\": {\"$month\": \"$field\"},"
                                + " \"EXPR$2\": {\"$week\": \"$field\"},"
                                + " \"EXPR$3\": {\"$dayOfMonth\": \"$field\"},"
                                + " \"EXPR$4\": {\"$dayOfWeek\": \"$field\"},"
                                + " \"EXPR$5\": {\"$dayOfYear\": \"$field\"},"
                                + " \"EXPR$6\": {\"$hour\": \"$field\"},"
                                + " \"EXPR$7\": {\"$minute\": \"$field\"},"
                                + " \"EXPR$8\": {\"$second\": \"$field\"},"
                                + " \"EXPR$9\":"
                                + " {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 3]}, 1,"
                                + " {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 6]}, 2,"
                                + " {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 9]}, 3,"
                                + " {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 12]}, 4,"
                                + " null]}]}]}]}, "
                                + "\"_id\": 0}}"),
                result.getAggregateOperations().get(0));

        final String timestampDiffQuery =
                String.format(
                        "SELECT "
                                + "TIMESTAMPDIFF(WEEK, \"field\", \"field\"), "
                                + "TIMESTAMPDIFF(DAY, \"field\", \"field\"), "
                                + "TIMESTAMPDIFF(HOUR, \"field\", \"field\"), "
                                + "TIMESTAMPDIFF(MINUTE, \"field\", \"field\"), "
                                + "TIMESTAMPDIFF(SECOND, \"field\", \"field\"), "
                                + "TIMESTAMPDIFF(MICROSECOND, \"field\", \"field\"), "
                                + "TIMESTAMPDIFF(YEAR, \"field\", \"field\"), "
                                + "TIMESTAMPDIFF(QUARTER, \"field\", \"field\"), "
                                + "TIMESTAMPDIFF(MONTH, \"field\", \"field\")"
                                + "FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        result = queryMapper.get(timestampDiffQuery);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(DATE_COLLECTION_NAME, result.getCollectionName());
        Assertions.assertEquals(9, result.getColumnMetaData().size());
        Assertions.assertEquals(1, result.getAggregateOperations().size());
        Assertions.assertEquals(BsonDocument.parse(
                    "{\"$project\":"
                            + " {\"EXPR$0\": {\"$divide\": [{\"$subtract\": [{\"$divide\": [{\"$subtract\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$mod\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$literal\": 1000}]}]}, {\"$literal\": 1000}]}, {\"$mod\": [{\"$divide\": [{\"$subtract\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$mod\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$literal\": 1000}]}]}, {\"$literal\": 1000}]}, {\"$literal\": 604800}]}]}, {\"$literal\": 604800}]},"
                            + " \"EXPR$1\": {\"$divide\": [{\"$subtract\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$mod\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$literal\": 86400000}]}]}, {\"$literal\": 86400000}]},"
                            + " \"EXPR$2\": {\"$divide\": [{\"$subtract\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$mod\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$literal\": 3600000}]}]}, {\"$literal\": 3600000}]},"
                            + " \"EXPR$3\": {\"$divide\": [{\"$subtract\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$mod\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$literal\": 60000}]}]}, {\"$literal\": 60000}]},"
                            + " \"EXPR$4\": {\"$divide\": [{\"$subtract\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$mod\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$literal\": 1000}]}]}, {\"$literal\": 1000}]},"
                            + " \"EXPR$5\": {\"$multiply\": [{\"$divide\": [{\"$subtract\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$mod\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$literal\": 1000}]}]}, {\"$literal\": 1000}]}, {\"$literal\": 1000000}]},"
                            + " \"EXPR$6\": {\"$subtract\": [{\"$year\": \"$field\"}, {\"$year\": \"$field\"}]},"
                            + " \"EXPR$7\": {\"$subtract\": ["
                            + "     {\"$add\": [{\"$multiply\": [4, {\"$year\": \"$field\"}]}, {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 3]}, 1, {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 6]}, 2, {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 9]}, 3, {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 12]}, 4, null]}]}]}]}]},"
                            + "     {\"$add\": [{\"$multiply\": [4, {\"$year\": \"$field\"}]}, {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 3]}, 1, {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 6]}, 2, {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 9]}, 3, {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 12]}, 4, null]}]}]}]}]}]},"
                            + " \"EXPR$8\": {\"$subtract\": [{\"$add\": [{\"$multiply\": [12, {\"$year\": \"$field\"}]}, {\"$month\": \"$field\"}]}, {\"$add\": [{\"$multiply\": [12, {\"$year\": \"$field\"}]}, {\"$month\": \"$field\"}]}]}, "
                            + " \"_id\": 0}}"),
        result.getAggregateOperations().get(0));
    }

    /**
     * Tests CURRENT_TIMESTAMP, CURRENT_DATE, and CURRENT_TIME.
     * @throws SQLException occurs if query fails.
     */
    @Test
    @DisplayName("Tests CURRENT_TIMESTAMP, CURRENT_DATE, and CURRENT_TIME.")
    void testCurrentTimestampFunctions() throws SQLException {
        final String currentTimestampQuery =
                String.format(
                        "SELECT CURRENT_TIMESTAMP AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext result1 = queryMapper.get(currentTimestampQuery);
        Assertions.assertNotNull(result1);
        Assertions.assertEquals(DATE_COLLECTION_NAME, result1.getCollectionName());
        Assertions.assertEquals(1, result1.getColumnMetaData().size());
        Assertions.assertEquals(1, result1.getAggregateOperations().size());
        BsonDocument rootDoc = result1.getAggregateOperations()
                .get(0).toBsonDocument(BsonDocument.class, null);
        Assertions.assertNotNull(rootDoc);
        BsonDocument addFieldsDoc = rootDoc.getDocument("$project");
        Assertions.assertNotNull(addFieldsDoc);
        BsonDateTime cstDateTime = addFieldsDoc.getDateTime("cts");
        Assertions.assertNotNull(cstDateTime);
        BsonDocument expectedDoc = BsonDocument.parse(
                "{\"$project\": "
                        + "{\"cts\": "
                        + "{\"$date\": "
                        + "{\"$numberLong\": "
                        + "\"" + cstDateTime.getValue() + "\""
                        + "}}, \"_id\": 0}}");
        Assertions.assertEquals(
                expectedDoc,
                result1.getAggregateOperations().get(0));

        final String currentDateQuery =
                String.format(
                        "SELECT CURRENT_DATE AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext result2 = queryMapper.get(currentDateQuery);
        Assertions.assertNotNull(result2);
        Assertions.assertEquals(DATE_COLLECTION_NAME, result2.getCollectionName());
        Assertions.assertEquals(1, result2.getColumnMetaData().size());
        Assertions.assertEquals(1, result2.getAggregateOperations().size());
        rootDoc = result2.getAggregateOperations()
                .get(0).toBsonDocument(BsonDocument.class, null);
        Assertions.assertNotNull(rootDoc);
        addFieldsDoc = rootDoc.getDocument("$project");
        Assertions.assertNotNull(addFieldsDoc);
        cstDateTime = addFieldsDoc.getDateTime("cts");
        Assertions.assertNotNull(cstDateTime);
        expectedDoc = BsonDocument.parse(
                "{\"$project\": "
                        + "{\"cts\": "
                        + "{\"$date\": "
                        + "{\"$numberLong\": "
                        + "\"" + cstDateTime.getValue() + "\""
                        + "}}, \"_id\": 0}}");
        Assertions.assertEquals(
                expectedDoc,
                result2.getAggregateOperations().get(0));

        final String currentTimeQuery =
                String.format(
                        "SELECT CURRENT_TIME AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext result3 = queryMapper.get(currentTimeQuery);
        Assertions.assertNotNull(result3);
        Assertions.assertEquals(DATE_COLLECTION_NAME, result3.getCollectionName());
        Assertions.assertEquals(1, result3.getColumnMetaData().size());
        Assertions.assertEquals(1, result3.getAggregateOperations().size());
        rootDoc = result3.getAggregateOperations()
                .get(0).toBsonDocument(BsonDocument.class, null);
        Assertions.assertNotNull(rootDoc);
        addFieldsDoc = rootDoc.getDocument("$project");
        Assertions.assertNotNull(addFieldsDoc);
        cstDateTime = addFieldsDoc.getDateTime("cts");
        Assertions.assertNotNull(cstDateTime);
        expectedDoc = BsonDocument.parse(
                "{\"$project\": "
                        + "{\"cts\": "
                        + "{\"$date\": "
                        + "{\"$numberLong\": "
                        + "\"" + cstDateTime.getValue() + "\""
                        + "}}, \"_id\": 0}}");
        Assertions.assertEquals(
                expectedDoc,
                result3.getAggregateOperations().get(0));
    }

    /**
     * Tests TIMESTAMPADD for MONTH, YEAR or QUARTER.
     */
    @Test
    @DisplayName("Tests TIMESTAMPADD for MONTH, YEAR or QUARTER.")
    void testTimestampAddMonthYearFunction() {
        final String timestampAddQuery8 =
                String.format(
                        "SELECT TIMESTAMPADD(MONTH, 10, \"field\") AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        Assertions.assertEquals(String.format("Unable to parse SQL"
                        + " 'SELECT TIMESTAMPADD(MONTH, 10, \"field\") AS \"cts\" FROM \"database\".\"dateTestCollection\"'. --"
                        + " Reason: 'Conversion between the source type (INTERVAL_MONTH) and the target type (TIMESTAMP) is not supported.'"),
                Assertions.assertThrows(SQLException.class, () -> queryMapper.get(timestampAddQuery8))
                        .getMessage());

        final String timestampAddQuery9 =
                String.format(
                        "SELECT TIMESTAMPADD(YEAR, 10, \"field\") AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        Assertions.assertEquals(String.format("Unable to parse SQL"
                        + " 'SELECT TIMESTAMPADD(YEAR, 10, \"field\") AS \"cts\" FROM \"database\".\"dateTestCollection\"'. --"
                        + " Reason: 'Conversion between the source type (INTERVAL_YEAR) and the target type (TIMESTAMP) is not supported.'"),
                Assertions.assertThrows(SQLException.class, () -> queryMapper.get(timestampAddQuery9))
                        .getMessage());

        final String timestampAddQuery10 =
                String.format(
                        "SELECT TIMESTAMPADD(QUARTER, 10, \"field\") AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        Assertions.assertEquals(String.format("Unable to parse SQL"
                        + " 'SELECT TIMESTAMPADD(QUARTER, 10, \"field\") AS \"cts\" FROM \"database\".\"dateTestCollection\"'. --"
                        + " Reason: 'Conversion between the source type (INTERVAL_MONTH) and the target type (TIMESTAMP) is not supported.'"),
                Assertions.assertThrows(SQLException.class, () -> queryMapper.get(timestampAddQuery10))
                        .getMessage());
    }

    /**
     * Tests DAYNAME.
     * @throws SQLException occurs if query fails.
     */
    @Test
    @DisplayName("Tests DAYNAME.")
    void testDayName() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT DAYNAME(\"field\") AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(1, operations.size());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"cts\":"
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 1]}, \"Sunday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 2]}, \"Monday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 3]}, \"Tuesday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 4]}, \"Wednesday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 5]}, \"Thursday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 6]}, \"Friday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 7]}, \"Saturday\","
                        + " null]}]}]}]}]}]}]}, "
                        + " \"_id\": 0}}"), operations.get(0));

        final String dayNameQuery2 =
                String.format(
                        "SELECT DAYNAME(NULL) AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context2 = queryMapper.get(dayNameQuery2);
        Assertions.assertNotNull(context2);
        final List<Bson> operations2 = context2.getAggregateOperations();
        Assertions.assertEquals(1, operations2.size());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"cts\":"
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": null}, 1]}, \"Sunday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": null}, 2]}, \"Monday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": null}, 3]}, \"Tuesday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": null}, 4]}, \"Wednesday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": null}, 5]}, \"Thursday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": null}, 6]}, \"Friday\","
                        + " {\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": null}, 7]}, \"Saturday\","
                        + " null]}]}]}]}]}]}]}, "
                        + " \"_id\": 0}}"), operations2.get(0));
    }

    /**
     * Tests MONTHNAME.
     * @throws SQLException occurs if query fails.
     */
    @Test
    @DisplayName("Tests MONTHNAME.")
    void testMonthName() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT MONTHNAME(\"field\") AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(1, operations.size());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"cts\":"
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 1]}, \"January\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 2]}, \"February\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 3]}, \"March\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 4]}, \"April\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 5]}, \"May\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 6]}, \"June\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 7]}, \"July\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 8]}, \"August\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 9]}, \"September\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 10]}, \"October\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 11]}, \"November\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 12]}, \"December\","
                        + " null]}]}]}]}]}]}]}]}]}]}]}]}, "
                        + " \"_id\": 0}}"), operations.get(0));

        final String dayNameQuery2 =
                String.format(
                        "SELECT MONTHNAME(NULL) AS \"cts\""
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context2 = queryMapper.get(dayNameQuery2);
        Assertions.assertNotNull(context2);
        final List<Bson> operations2 = context2.getAggregateOperations();
        Assertions.assertEquals(1, operations2.size());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"cts\":"
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 1]}, \"January\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 2]}, \"February\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 3]}, \"March\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 4]}, \"April\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 5]}, \"May\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 6]}, \"June\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 7]}, \"July\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 8]}, \"August\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 9]}, \"September\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 10]}, \"October\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 11]}, \"November\","
                        + " {\"$cond\": [{\"$eq\": [{\"$month\": null}, 12]}, \"December\","
                        + " null]}]}]}]}]}]}]}]}]}]}]}]}, "
                        + " \"_id\": 0 }}"), operations2.get(0));
    }

    @Test
    @DisplayName("Tests FLOOR(ts TO <x>).")
    void testFloorForDate() throws SQLException {
        final String floorDayQuery =
                String.format(
                        "SELECT"
                                + " FLOOR(\"field\" TO YEAR),"
                                + " FLOOR(\"field\" TO MONTH),"
                                + " FLOOR(\"field\" TO DAY),"
                                + " FLOOR(\"field\" TO HOUR),"
                                + " FLOOR(\"field\" TO MINUTE),"
                                + " FLOOR(\"field\" TO SECOND),"
                                + " FLOOR(\"field\" TO MILLISECOND)"
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(floorDayQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(1, operations.size());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\":"
                        + " {\"EXPR$0\": {\"$dateFromString\": {\"dateString\":"
                        + " {\"$dateToString\": {\"date\": \"$field\", \"format\": \"%Y-01-01T00:00:00Z\"}}}},"
                        + " \"EXPR$1\": {\"$dateFromString\": {\"dateString\":"
                        + " {\"$dateToString\": {\"date\": \"$field\", \"format\": \"%Y-%m-01T00:00:00Z\"}}}},"
                        + " \"EXPR$2\": {\"$add\": [{\"$date\": \"1970-01-01T00:00:00Z\"},"
                        + " {\"$multiply\": [86400000, {\"$divide\": [{\"$subtract\":"
                        + " [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " {\"$mod\": [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " 86400000]}]}, 86400000]}]}]},"
                        + " \"EXPR$3\": {\"$add\": [{\"$date\": \"1970-01-01T00:00:00Z\"},"
                        + " {\"$multiply\": [3600000, {\"$divide\": [{\"$subtract\":"
                        + " [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " {\"$mod\": [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " 3600000]}]}, 3600000]}]}]},"
                        + " \"EXPR$4\": {\"$add\": [{\"$date\": \"1970-01-01T00:00:00Z\"},"
                        + " {\"$multiply\": [60000, {\"$divide\": [{\"$subtract\":"
                        + " [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " {\"$mod\": [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " 60000]}]}, 60000]}]}]},"
                        + " \"EXPR$5\": {\"$add\": [{\"$date\": \"1970-01-01T00:00:00Z\"},"
                        + " {\"$multiply\": [1000, {\"$divide\": [{\"$subtract\":"
                        + " [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " {\"$mod\": [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " 1000]}]}, 1000]}]}]},"
                        + " \"EXPR$6\": {\"$add\": [{\"$date\": \"1970-01-01T00:00:00Z\"},"
                        + " {\"$multiply\": [1, {\"$divide\": [{\"$subtract\":"
                        + " [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " {\"$mod\": [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-01T00:00:00Z\"}]},"
                        + " 1]}]}, 1]}]}]}, "
                        + " \"_id\": 0}}").toJson(),
                ((BsonDocument) operations.get(0)).toJson());

        final String floorDayQuery1 =
                String.format(
                        "SELECT"
                                + " FLOOR(\"field\" TO WEEK)"
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context1 = queryMapper.get(floorDayQuery1);
        Assertions.assertNotNull(context1);
        final List<Bson> operations1 = context1.getAggregateOperations();
        Assertions.assertEquals(1, operations1.size());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\":"
                        + " {\"EXPR$0\": "
                        + "   {\"$add\": ["
                        + "     {\"$date\": \"1970-01-05T00:00:00Z\"}, "
                        + "     {\"$multiply\": [604800000, "
                        + "       {\"$divide\": ["
                        + "         {\"$subtract\": ["
                        + "           {\"$subtract\": [\"$field\", {\"$date\": \"1970-01-05T00:00:00Z\"}]}, "
                        + "           {\"$mod\": [{\"$subtract\": [\"$field\", {\"$date\": \"1970-01-05T00:00:00Z\"}]}, 604800000]}]}, 604800000]}]}]}, "
                        + " \"_id\": 0}}").toJson(),
                ((BsonDocument) operations1.get(0)).toJson());

        final String floorDayQuery2 =
                String.format(
                        "SELECT TIMESTAMPADD(WEEK,%n"
                                + " TIMESTAMPDIFF(WEEK,%n"
                                + "   TIMESTAMP '1970-01-05', \"field\"),%n"
                                + " TIMESTAMP '1970-01-05')%n"
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context2 = queryMapper.get(floorDayQuery2);
        Assertions.assertNotNull(context2);
        final List<Bson> operations2 = context2.getAggregateOperations();
        Assertions.assertEquals(1, operations2.size());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\":"
                        + " {\"EXPR$0\":"
                        + "   {\"$add\": ["
                        + "     {\"$date\": \"1970-01-05T00:00:00Z\"}, "
                        + "     {\"$multiply\": ["
                        + "      {\"$literal\": {\"$numberLong\": \"604800000\"}}, "
                        + "       {\"$divide\": ["
                        + "         {\"$subtract\": ["
                        + "           {\"$divide\": ["
                        + "             {\"$subtract\": ["
                        + "               {\"$subtract\": ["
                        + "                 \"$field\", "
                        + "                 {\"$date\": \"1970-01-05T00:00:00Z\"}]"
                        + "               }, "
                        + "               {\"$mod\": ["
                        + "                 {\"$subtract\": ["
                        + "                   \"$field\", "
                        + "                   {\"$date\": \"1970-01-05T00:00:00Z\"}]"
                        + "                 }, "
                        + "                 {\"$literal\": 1000}]"
                        + "               }]"
                        + "             }, "
                        + "             {\"$literal\": 1000}]"
                        + "           }, "
                        + "           {\"$mod\": ["
                        + "             {\"$divide\": ["
                        + "               {\"$subtract\": ["
                        + "                 {\"$subtract\": ["
                        + "                   \"$field\", "
                        + "                   {\"$date\": \"1970-01-05T00:00:00Z\"}]"
                        + "                 }, "
                        + "                 {\"$mod\": ["
                        + "                   {\"$subtract\": ["
                        + "                     \"$field\", "
                        + "                     {\"$date\": \"1970-01-05T00:00:00Z\"}]"
                        + "                   }, "
                        + "                   {\"$literal\": 1000}]"
                        + "                 }]"
                        + "               }, "
                        + "               {\"$literal\": 1000}]"
                        + "             }, "
                        + "             {\"$literal\": 604800}]"
                        + "           }]"
                        + "         }, "
                        + "         {\"$literal\": 604800}]"
                        + "       }]"
                        + "     }]"
                        + "   }, "
                        + " \"_id\": 0 "
                        + " }"
                        + "}").toJson(),
                ((BsonDocument) operations2.get(0)).toJson());

        final String floorDayQuery3 =
                String.format(
                        "SELECT FLOOR(\"field\" TO QUARTER)"
                                + " FROM \"%s\".\"%s\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context3 = queryMapper.get(floorDayQuery3);
        Assertions.assertNotNull(context3);
        final List<Bson> operations3 = context3.getAggregateOperations();
        Assertions.assertEquals(1, operations3.size());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\":"
                        + " {\"EXPR$0\": "
                        + "   {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 3]},"
                        + "     {\"$dateFromString\": {\"dateString\": {\"$dateToString\": {\"date\": \"$field\", \"format\": \"%Y-01-01T00:00:00Z\"}}}},"
                        + "   {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 6]}, "
                        + "     {\"$dateFromString\": {\"dateString\": {\"$dateToString\": {\"date\": \"$field\", \"format\": \"%Y-04-01T00:00:00Z\"}}}},"
                        + "   {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 9]},"
                        + "     {\"$dateFromString\": {\"dateString\": {\"$dateToString\": {\"date\": \"$field\", \"format\": \"%Y-07-01T00:00:00Z\"}}}},"
                        + "   {\"$cond\": [{\"$lte\": [{\"$month\": \"$field\"}, 12]},"
                        + "     {\"$dateFromString\": {\"dateString\": {\"$dateToString\": {\"date\": \"$field\", \"format\": \"%Y-10-01T00:00:00Z\"}}}},"
                        + "   null]}]}]}]}, "
                        + " \"_id\": 0 }}").toJson(),
                ((BsonDocument) operations3.get(0)).toJson());
    }

    @Test
    @DisplayName("Tests MONTHNAME in WHERE clause.")
    void testWhereMonthName() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" WHERE MONTHNAME(\"field\") = 'February'",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(4, operations.size());
        Assertions.assertEquals(
                BsonDocument.parse(
                        "{\"$project\": {"
                                + "\"_id\": 1, "
                                + "\"field\": 1, "
                                + DocumentDbFilter.BOOLEAN_FLAG_FIELD
                                + ": {\"$cond\": [{\"$and\": [{\"$gt\": ["
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 1]}, \"January\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 2]}, \"February\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 3]}, \"March\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 4]}, \"April\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 5]}, \"May\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 6]}, \"June\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 7]}, \"July\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 8]}, \"August\", {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 9]}, \"September\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 10]}, \"October\", {\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 11]}, \"November\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 12]}, \"December\", null]}]}]}]}]}]}]}]}]}]}]}]}, null]}, "
                                + "{\"$gt\": [{\"$literal\": \"February\"}, null]}]}, "
                                + "{\"$eq\": [{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 1]}, \"January\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 2]}, \"February\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 3]}, \"March\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 4]}, \"April\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 5]}, \"May\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 6]}, \"June\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 7]}, \"July\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 8]}, \"August\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 9]}, \"September\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 10]}, \"October\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 11]}, \"November\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$month\": \"$field\"}, 12]}, \"December\", null]}]}]}]}]}]}]}]}]}]}]}]}, "
                                + "{\"$literal\": \"February\"}]}, null]}}}"),
        operations.get(0));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$match\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": {\"$eq\": true}}}"), operations.get(1));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": 0}}"), operations.get(2));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"),
                operations.get(3));
    }

    @Test
    @DisplayName("Tests DAYNAME in WHERE clause.")
    void testWhereDayName() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" WHERE DAYNAME(\"field\") = 'Tuesday'",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(4, operations.size());
        Assertions.assertEquals(
                BsonDocument.parse(
                        "{\"$project\": {"
                                + "\"_id\": 1, "
                                + "\"field\": 1, "
                                + DocumentDbFilter.BOOLEAN_FLAG_FIELD
                                + ": {\"$cond\": [{\"$and\": [{\"$gt\": ["
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 1]}, \"Sunday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 2]}, \"Monday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 3]}, \"Tuesday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 4]}, \"Wednesday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 5]}, \"Thursday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 6]}, \"Friday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 7]}, \"Saturday\", null]}]}]}]}]}]}]}, null]}, "
                                + "{\"$gt\": [{\"$literal\": \"Tuesday\"}, null]}]}, "
                                + "{\"$eq\": [{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 1]}, \"Sunday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 2]}, \"Monday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 3]}, \"Tuesday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 4]}, \"Wednesday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 5]}, \"Thursday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 6]}, \"Friday\", "
                                + "{\"$cond\": [{\"$eq\": [{\"$dayOfWeek\": \"$field\"}, 7]}, \"Saturday\", null]}]}]}]}]}]}]}, "
                                + "{\"$literal\": \"Tuesday\"}]}, null]}}}"),
                operations.get(0));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$match\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": {\"$eq\": true}}}"), operations.get(1));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": 0}}"), operations.get(2));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"),
                operations.get(3));
    }

    @Test
    @DisplayName("Tests CURRENT_DATE in WHERE clause.")
    void testWhereCurrentDate() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" WHERE \"field\" <> CURRENT_DATE",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(2, operations.size());
        final BsonDocument rootDoc = context.getAggregateOperations()
                .get(0).toBsonDocument(BsonDocument.class, null);
        Assertions.assertNotNull(rootDoc);
        final BsonDocument matchDoc = rootDoc.getDocument("$match");
        Assertions.assertNotNull(matchDoc);
        final BsonDateTime cstDateTime = matchDoc.getDocument("field").getArray("$nin").get(1).asDateTime();
        Assertions.assertNotNull(cstDateTime);
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$match\": {\"field\": { \"$nin\": [null, {\"$date\": {\"$numberLong\": \"" + cstDateTime.getValue() + "\"}}]}}}"),
                operations.get(0));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"), operations.get(1));
    }

    @Test
    @DisplayName("Tests CURRENT_TIME in WHERE clause.")
    void testWhereCurrentTime() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" WHERE CAST(\"field\" AS TIME) <> CURRENT_TIME",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(2, operations.size());
        final BsonDocument rootDoc = context.getAggregateOperations()
                .get(0).toBsonDocument(BsonDocument.class, null);
        Assertions.assertNotNull(rootDoc);
        final BsonDocument matchDoc = rootDoc.getDocument("$match");
        Assertions.assertNotNull(matchDoc);
        final BsonDateTime cstDateTime = matchDoc.getDocument("field").getArray("$nin").get(1).asDateTime();
        Assertions.assertNotNull(cstDateTime);
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$match\": {\"field\": { \"$nin\": [null, {\"$date\": {\"$numberLong\": \"" + cstDateTime.getValue() + "\"}}]}}}"),
                operations.get(0));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"), operations.get(1));
    }

    @Test
    @DisplayName("Tests CURRENT_TIMESTAMP in WHERE clause.")
    void testWhereCurrentTimestamp() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" WHERE CAST(\"field\" as TIMESTAMP) <> CURRENT_TIMESTAMP",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(2, operations.size());
        final BsonDocument rootDoc = context.getAggregateOperations()
                .get(0).toBsonDocument(BsonDocument.class, null);
        Assertions.assertNotNull(rootDoc);
        final BsonDocument matchDoc = rootDoc.getDocument("$match");
        Assertions.assertNotNull(matchDoc);
        final BsonDateTime cstDateTime = matchDoc.getDocument("field").getArray("$nin").get(1).asDateTime();
        Assertions.assertNotNull(cstDateTime);
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$match\": {\"field\": { \"$nin\": [null, {\"$date\": {\"$numberLong\": \"" + cstDateTime.getValue() + "\"}}]}}}"),
                operations.get(0));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"), operations.get(1));
    }

    @Test
    @DisplayName("Tests date extract in WHERE clause.")
    void testWhereExtract() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" " +
                                "WHERE EXTRACT(YEAR FROM \"field\") = 2021",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(4, operations.size());
        Assertions.assertEquals(
                BsonDocument.parse(
                        "{\"$project\": {"
                                + "\"_id\": 1, "
                                + "\"field\": 1, "
                                + DocumentDbFilter.BOOLEAN_FLAG_FIELD
                                + ": {\"$cond\": [{\"$and\": [{\"$gt\": [{\"$year\": \"$field\"}, null]}, "
                                + "{\"$gt\": [ {\"$literal\": {\"$numberLong\": \"2021\"}}, null]}]}, {\"$eq\": [{\"$year\": \"$field\"}, {\"$literal\": {\"$numberLong\": \"2021\"}}]}, null]}}}"),
                operations.get(0));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$match\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": {\"$eq\": true}}}"), operations.get(1));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": 0}}"), operations.get(2));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"),
                operations.get(3));
    }

    @Test
    @DisplayName("Tests TIMESTAMPADD in WHERE clause.")
    void testWhereTimestampAdd() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" " +
                                "WHERE TIMESTAMPADD(DAY, 3, \"field\") = '2020-01-04'",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(4, operations.size());
        Assertions.assertEquals(
                BsonDocument.parse(
                        "{\"$project\": {"
                                + "\"_id\": 1, "
                                + "\"field\": 1,"
                                + DocumentDbFilter.BOOLEAN_FLAG_FIELD
                                + ": {\"$cond\": [{\"$and\": [{\"$gt\": [{\"$add\": [\"$field\", {\"$literal\": {\"$numberLong\": \"259200000\"}}]}, null]}, "
                                + "{\"$gt\": [{\"$date\": \"2020-01-04T00:00:00Z\"}, null]}]}, "
                                + "{\"$eq\": [{\"$add\": [\"$field\", {\"$literal\": {\"$numberLong\": \"259200000\"}}]}, {\"$date\": \"2020-01-04T00:00:00Z\"}]}, null]}}}"),
                operations.get(0));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$match\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": {\"$eq\": true}}}"), operations.get(1));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": 0}}"), operations.get(2));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"),
                operations.get(3));
    }

    @Test
    @DisplayName("Tests TIMESTAMPADD on right in WHERE clause.")
    void testWhereTimestampAddOnRightOrLeft() throws SQLException {
        final String dayNameQuery1 =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" " +
                                "WHERE \"field\" <= TIMESTAMPADD(DAY, -3, CURRENT_DATE)",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context1 = queryMapper.get(dayNameQuery1);
        Assertions.assertNotNull(context1);
        final List<Bson> operations1 = context1.getAggregateOperations();
        Assertions.assertEquals(2, operations1.size());
        final BsonDocument rootDoc1 = operations1.get(0).toBsonDocument();
        final BsonDocument matchDoc1 = rootDoc1.getDocument("$match");
        final BsonDateTime currDate1 = matchDoc1
                .getDocument("field")
                .getDateTime("$lte");
        Assertions.assertNotNull(currDate1);
        Assertions.assertEquals(
                BsonDocument.parse("{\"$match\": {\"field\": {\"$lte\": {\"$date\": {\"$numberLong\": \"" + currDate1.getValue() + "\"}}}}}").toJson(),
                operations1.get(0).toBsonDocument().toJson());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"), operations1.get(1));

        final String dayNameQuery2 =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" " +
                                "WHERE \"field\" <= TIMESTAMPADD(DAY, -3, CURRENT_TIMESTAMP)",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context2 = queryMapper.get(dayNameQuery2);
        Assertions.assertNotNull(context2);
        final List<Bson> operations2 = context2.getAggregateOperations();
        Assertions.assertEquals(2, operations2.size());
        final BsonDocument rootDoc2 = operations2.get(0).toBsonDocument();
        final BsonDocument matchDoc2 = rootDoc2.getDocument("$match");
        final BsonDateTime currDate2 = matchDoc2
                .getDocument("field")
                .getDateTime("$lte");
        Assertions.assertNotNull(currDate2);
        Assertions.assertEquals(
                BsonDocument.parse("{\"$match\": {\"field\": {\"$lte\": {\"$date\": {\"$numberLong\": \"" + currDate2.getValue() + "\"}}}}}").toJson(),
                operations2.get(0).toBsonDocument().toJson());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"), operations2.get(1));

        final String dayNameQuery3 =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" " +
                                "WHERE TIMESTAMPADD(DAY, -3, CURRENT_DATE) >= \"field\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context3 = queryMapper.get(dayNameQuery3);
        Assertions.assertNotNull(context3);
        final List<Bson> operations3 = context3.getAggregateOperations();
        Assertions.assertEquals(2, operations3.size());
        final BsonDocument rootDoc3 = operations3.get(0).toBsonDocument();
        final BsonDocument matchDoc3 = rootDoc3.getDocument("$match");
        final BsonDateTime currDate3 = matchDoc3
                .getDocument("field")
                .getDateTime("$lte");
        Assertions.assertNotNull(currDate3);
        Assertions.assertEquals(
                BsonDocument.parse("{\"$match\": {\"field\": {\"$lte\": {\"$date\": {\"$numberLong\": \"" + currDate3.getValue() + "\"}}}}}").toJson(),
                operations3.get(0).toBsonDocument().toJson());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"), operations3.get(1));

        final String dayNameQuery4 =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" " +
                                "WHERE TIMESTAMPADD(DAY, -3, CURRENT_TIMESTAMP) >= \"field\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context4 = queryMapper.get(dayNameQuery4);
        Assertions.assertNotNull(context4);
        final List<Bson> operations4 = context4.getAggregateOperations();
        Assertions.assertEquals(2, operations4.size());
        final BsonDocument rootDoc4 = operations4.get(0).toBsonDocument();
        final BsonDocument matchDoc4 = rootDoc4.getDocument("$match");
        final BsonDateTime currDate4 = matchDoc4
                .getDocument("field")
                .getDateTime("$lte");
        Assertions.assertNotNull(currDate4);
        Assertions.assertEquals(
                BsonDocument.parse("{\"$match\": {\"field\": {\"$lte\": {\"$date\": {\"$numberLong\": \"" + currDate4.getValue() + "\"}}}}}").toJson(),
                operations4.get(0).toBsonDocument().toJson());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"), operations4.get(1));

        final String dayNameQuery5 =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" " +
                                "WHERE \"field\" <= TIMESTAMPADD(DAY, -3, DATE '2020-01-04')",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context5 = queryMapper.get(dayNameQuery5);
        Assertions.assertNotNull(context5);
        final List<Bson> operations5 = context5.getAggregateOperations();
        Assertions.assertEquals(2, operations5.size());
        Assertions.assertEquals(
                BsonDocument.parse("{\"$match\": {\"field\": {\"$lte\": {\"$date\": \"2020-01-01T00:00:00Z\"}}}}}").toJson(),
                operations5.get(0).toBsonDocument().toJson());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"), operations5.get(1));

        final String dayNameQuery6 =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" " +
                                "WHERE TIMESTAMPADD(DAY, -3, DATE '2020-01-04') >= \"field\"",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context6 = queryMapper.get(dayNameQuery6);
        Assertions.assertNotNull(context6);
        final List<Bson> operations6 = context6.getAggregateOperations();
        Assertions.assertEquals(2, operations6.size());
        Assertions.assertEquals(
                BsonDocument.parse("{\"$match\": {\"field\": {\"$lte\": {\"$date\": \"2020-01-01T00:00:00Z\"}}}}}").toJson(),
                operations6.get(0).toBsonDocument().toJson());
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"), operations6.get(1));
    }

    @Test
    @DisplayName("Tests TIMESTAMPDIFF in WHERE clause.")
    void testWhereTimestampDiff() throws SQLException {
        final String dayNameQuery =
                String.format(
                        "SELECT * FROM \"%s\".\"%s\" " +
                                "WHERE TIMESTAMPDIFF(DAY, \"field\", \"field\") = 0",
                        getDatabaseName(), DATE_COLLECTION_NAME);
        final DocumentDbMqlQueryContext context = queryMapper.get(dayNameQuery);
        Assertions.assertNotNull(context);
        final List<Bson> operations = context.getAggregateOperations();
        Assertions.assertEquals(4, operations.size());
        Assertions.assertEquals(
                BsonDocument.parse(
                        "{\"$project\": {"
                                + "\"_id\": 1, "
                                + "\"field\": 1, "
                                + DocumentDbFilter.BOOLEAN_FLAG_FIELD
                                + ": {\"$cond\": [{\"$and\": [{\"$gt\": [{\"$divide\": [{\"$subtract\": [{\"$subtract\": [\"$field\", \"$field\"]}, "
                                + "{\"$mod\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$literal\": 86400000}]}]}, {\"$literal\": 86400000}]}, null]}, "
                                + "{\"$gt\": [{\"$literal\": 0}, null]}]}, {\"$eq\": [{\"$divide\": [{\"$subtract\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$mod\": [{\"$subtract\": [\"$field\", \"$field\"]}, {\"$literal\": 86400000}]}]}, {\"$literal\": 86400000}]}, {\"$literal\": 0}]}, null]}}}"),
                operations.get(0));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$match\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": {\"$eq\": true}}}"), operations.get(1));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {" + DocumentDbFilter.BOOLEAN_FLAG_FIELD + ": 0}}"), operations.get(2));
        Assertions.assertEquals(BsonDocument.parse(
                "{\"$project\": {\"dateTestCollection__id\": \"$_id\", \"field\": \"$field\", \"_id\": 0}}"),
                operations.get(3));
    }
}
