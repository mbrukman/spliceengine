/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.derby.impl.sql.execute.operations;

import com.splicemachine.derby.test.framework.SpliceDataWatcher;
import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceTableWatcher;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Tests for Views
 * @author Scott Fines
 * Created on: 6/25/13
 */
public class ViewIT { 
    private static final Logger LOG = Logger.getLogger(ViewIT.class);

    protected static SpliceWatcher spliceClassWatcher = new SpliceWatcher();

    protected static SpliceSchemaWatcher schemaWatcher = new SpliceSchemaWatcher(ViewIT.class.getSimpleName());

    protected static SpliceTableWatcher baseTableWatcher = new SpliceTableWatcher("t1",schemaWatcher.schemaName,"(a int, b int)");

    private static int size = 10;
    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
            .around(schemaWatcher)
            .around(baseTableWatcher)
            .around(new SpliceDataWatcher(){

                @Override
                protected void starting(Description description) {
                    try {
                        PreparedStatement ps = spliceClassWatcher.prepareStatement("insert into "+ baseTableWatcher+" (a, b) values (?,?)");
                        for(int i=0;i<size;i++){
                            ps.setInt(1,i);
                            ps.setInt(2,i*2);
                            ps.executeUpdate();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });

    @Rule public SpliceWatcher methodWatcher = new SpliceWatcher();

    @Test
    public void testCanUseView() throws Exception {


    }

    @Test
    public void testCanCreateViewWithLimitAndGroupedBy() throws Exception {
        // Regression test for Bug 579
        methodWatcher.prepareStatement("create view t1_view (a,b) as select a, count(b) from "+ baseTableWatcher +" group by a").executeUpdate();
        try{
            ResultSet rs = methodWatcher.executeQuery("select * from t1_view {limit 1}");
            int count=0;
            while(rs.next()){
                System.out.println(rs.getInt(1)+"+"+rs.getInt(2));
                count++;
            }
            Assert.assertEquals(1,count);
        }finally{
            methodWatcher.prepareStatement("drop view t1_view").execute();
        }
    }
}
