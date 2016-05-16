package com.github.labai.opa;

import com.github.labai.opa.IntTests.AblIntTestBase;
import com.github.labai.opa.IntTests.SampleTable;
import com.github.labai.opa.Opa.IoDir;
import com.github.labai.opa.Opa.OpaParam;
import com.github.labai.opa.Opa.OpaProc;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Ignore
public class I04TableInOutTest extends AblIntTestBase{

	@OpaProc(proc="jpx/test/opa/test_04_table_inout.p")
	static class TablesInOutOpp {
		@OpaParam
		public String someId;


		@OpaParam(table=SampleTable.class, io=IoDir.OUT)
		public List<SampleTable> ttout;

		@OpaParam(table=SampleTable.class, io=IoDir.INOUT)
		public List<SampleTable> ttinout = new ArrayList<SampleTable>();;

		@OpaParam(table=SampleTable.class, io=IoDir.IN)
		public List<SampleTable> ttin = new ArrayList<SampleTable>();

		@OpaParam(io=IoDir.OUT)
		public String errorCode;

		@OpaParam(io=IoDir.OUT)
		public String errorMessage;
	}

	/*
	 *
	 *
	 */
	//@Ignore
	@Test
	public void testTableOutSetter() throws OpaException, CloneNotSupportedException {

		TablesInOutOpp opp = new TablesInOutOpp();
		opp.someId = "00000038";

		//
		// our test setter setIntVal increases value by 100000.
		// After that test sends data to appServer, retrieves same value
		// and calls setter to assign value from server
		// (increase by 100000 again)
		//

		SampleTable tt1 = new SampleTable();
		tt1.charVal = "sending something";
		tt1.setIntVal(1);
		tt1.int64Val = 1L;
		tt1.decVal = new BigDecimal("1.1");
		tt1.logVal = true;
		// .. + other fields

		SampleTable tt2 = tt1.clone();

		// in
		opp.ttin = new ArrayList<SampleTable>();
		opp.ttin.add(tt1);

		// in-out
		opp.ttinout = new ArrayList<SampleTable>();
		opp.ttinout.add(tt2);

		server.runProc(opp);

		String sout0 = "sending something|200001|1|1.1|null|true|null|null|null";
		String sinout0 = "sending something|200002|1|1.1|null|true|null|null|null";

		/*
		for (SampleTable tt: opp.ttout)
			System.out.println(IntTests.sampleTableToString(tt));
		for (SampleTable tt: opp.ttinout)
			System.out.println(IntTests.sampleTableToString(tt));
		*/
		Assert.assertEquals(sout0, opp.ttout.get(0).toString());
		Assert.assertEquals(sinout0, opp.ttinout.get(0).toString());

	}

}

