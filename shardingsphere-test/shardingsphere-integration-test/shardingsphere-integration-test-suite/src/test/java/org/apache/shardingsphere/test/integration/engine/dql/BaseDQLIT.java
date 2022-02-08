/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.test.integration.engine.dql;

import org.apache.shardingsphere.test.integration.cases.dataset.metadata.DataSetColumn;
import org.apache.shardingsphere.test.integration.cases.dataset.metadata.DataSetMetaData;
import org.apache.shardingsphere.test.integration.cases.dataset.row.DataSetRow;
import org.apache.shardingsphere.test.integration.engine.SingleITCase;
import org.apache.shardingsphere.test.integration.env.EnvironmentPath;
import org.apache.shardingsphere.test.integration.env.dataset.DataSetEnvironmentManager;
import org.apache.shardingsphere.test.integration.framework.param.model.AssertionParameterizedArray;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public abstract class BaseDQLIT extends SingleITCase {
    
    public BaseDQLIT(final AssertionParameterizedArray parameter) {
        super(parameter);
    }
    
    @Override
    public void init() throws Exception {
        super.init();
        compose.executeOnStarted(compose -> {
            try {
                new DataSetEnvironmentManager(
                        EnvironmentPath.getDataSetFile(getScenario()),
                        getStorageContainer().getDataSourceMap()
                ).fillData();
            } catch (IOException | JAXBException | SQLException | ParseException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    protected final void assertResultSet(final ResultSet resultSet) throws SQLException {
        assertMetaData(resultSet.getMetaData(), getExpectedColumns());
        if (getDataSet().isIgnoreRowOrder()) {
            assertRowsIgnoreOrder(resultSet, getDataSet().getRows());
        } else {
            assertRows(resultSet, getDataSet().getRows());
        }
    }
    
    private Collection<DataSetColumn> getExpectedColumns() {
        Collection<DataSetColumn> result = new LinkedList<>();
        for (DataSetMetaData each : getDataSet().getMetaDataList()) {
            result.addAll(each.getColumns());
        }
        return result;
    }
    
    private void assertMetaData(final ResultSetMetaData actual, final Collection<DataSetColumn> expected) throws SQLException {
        assertThat(actual.getColumnCount(), is(expected.size()));
        int index = 1;
        for (DataSetColumn each : expected) {
            assertThat(actual.getColumnLabel(index++).toLowerCase(), is(each.getName().toLowerCase()));
        }
    }
    
    private void assertRows(final ResultSet actual, final List<DataSetRow> expected) throws SQLException {
        int rowCount = 0;
        ResultSetMetaData actualMetaData = actual.getMetaData();
        while (actual.next()) {
            assertTrue("Size of actual result set is different with size of expected dat set rows.", rowCount < expected.size());
            assertRow(actual, actualMetaData, expected.get(rowCount));
            rowCount++;
        }
        assertThat("Size of actual result set is different with size of expected dat set rows.", rowCount, is(expected.size()));
    }
    
    private void assertRowsIgnoreOrder(final ResultSet actual, final List<DataSetRow> expected) throws SQLException {
        int rowCount = 0;
        ResultSetMetaData actualMetaData = actual.getMetaData();
        while (actual.next()) {
            assertTrue("Size of actual result set is different with size of expected dat set rows.", rowCount < expected.size());
            assertTrue(String.format("Actual result set does not exist in expected, row count [%d].", rowCount), assertContains(actual, actualMetaData, expected));
            rowCount++;
        }
        assertThat("Size of actual result set is different with size of expected dat set rows.", rowCount, is(expected.size()));
    }
    
    private boolean assertContains(final ResultSet actual, final ResultSetMetaData actualMetaData, final List<DataSetRow> expected) throws SQLException {
        for (DataSetRow each : expected) {
            if (isSameRow(actual, actualMetaData, each)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isSameRow(final ResultSet actual, final ResultSetMetaData actualMetaData, final DataSetRow expected) throws SQLException {
        int columnIndex = 1;
        for (String each : expected.splitValues(",")) {
            if (!isSameDateValue(actual, columnIndex, actualMetaData.getColumnLabel(columnIndex), each)) {
                return false;
            }
            columnIndex++;
        }
        return true;
    }
    
    private boolean isSameDateValue(final ResultSet actual, final int columnIndex, final String columnLabel, final String expected) throws SQLException {
        if (Types.DATE == actual.getMetaData().getColumnType(columnIndex)) {
            assertDateValue(actual, columnIndex, columnLabel, expected);
            if (NOT_VERIFY_FLAG.equals(expected)) {
                return true;
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return expected.equals(dateFormat.format(actual.getDate(columnIndex))) && expected.equals(dateFormat.format(actual.getDate(columnLabel)));
        } else {
            return expected.equals(String.valueOf(actual.getObject(columnIndex))) && expected.equals(String.valueOf(actual.getObject(columnLabel)));
        }
    }
    
    private void assertRow(final ResultSet actual, final ResultSetMetaData actualMetaData, final DataSetRow expected) throws SQLException {
        int columnIndex = 1;
        for (String each : expected.splitValues(",")) {
            String columnLabel = actualMetaData.getColumnLabel(columnIndex);
            if (Types.DATE == actual.getMetaData().getColumnType(columnIndex)) {
                assertDateValue(actual, columnIndex, columnLabel, each);
            } else {
                assertObjectValue(actual, columnIndex, columnLabel, each);
            }
            columnIndex++;
        }
    }
    
    private void assertDateValue(final ResultSet actual, final int columnIndex, final String columnLabel, final String expected) throws SQLException {
        if (NOT_VERIFY_FLAG.equals(expected)) {
            return;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        assertThat(dateFormat.format(actual.getDate(columnIndex)), is(expected));
        assertThat(dateFormat.format(actual.getDate(columnLabel)), is(expected));
    }
    
    private void assertObjectValue(final ResultSet actual, final int columnIndex, final String columnLabel, final String expected) throws SQLException {
        assertThat(String.valueOf(actual.getObject(columnIndex)), is(expected));
        assertThat(String.valueOf(actual.getObject(columnLabel)), is(expected));
    }
}