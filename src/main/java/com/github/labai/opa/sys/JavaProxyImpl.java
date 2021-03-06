package com.github.labai.opa.sys;

import com.progress.common.ehnlog.IAppLogger;
import com.progress.open4gl.ConnectException;
import com.progress.open4gl.Open4GLException;
import com.progress.open4gl.ResultSetHolder;
import com.progress.open4gl.SystemErrorException;
import com.progress.open4gl.dynamicapi.IPoolProps;
import com.progress.open4gl.dynamicapi.MetaSchema;
import com.progress.open4gl.dynamicapi.ParameterSet;
import com.progress.open4gl.dynamicapi.ResultSet;
import com.progress.open4gl.dynamicapi.RqContext;
import com.progress.open4gl.javaproxy.AppObject;
import com.github.labai.opa.sys.Exceptions.OpaSessionTimeoutException;
import com.github.labai.opa.sys.Exceptions.OpaStructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Augustus
 *
 * For internal usage only (is not part of api)
 *
 */
class JavaProxyImpl extends AppObject {
	private final static Logger logger = LoggerFactory.getLogger(JavaProxyImpl.class);

	private Supplier<String> requestIdGenerator = null;

	public JavaProxyImpl(String s, IPoolProps ipoolprops, IAppLogger iapplogger) throws Open4GLException, ConnectException, SystemErrorException {
		super(s, ipoolprops, iapplogger, null);
	}

	// procName can be null, but then annotation @OpaProc must have "proc" parameter
	//
	public String runProc(Object opp, String procName, Supplier<String> requestIdProvider) throws Open4GLException, SQLException, OpaStructureException, OpaSessionTimeoutException {

		if (!isSessionAvailable())
			throw new OpaSessionTimeoutException();

		// read bean and fill params
		//
		ParameterSet paramSet = null;
		try {
			paramSet = ParamUtils.beanToParam(opp);
		} catch (IllegalAccessException e) {
			throw new OpaStructureException("Error while reading bean", e);
		}

		MetaSchema metaschema = TableUtils.extractMetaSchema(opp.getClass());

		// run procedure
		//
		if (procName == null) {
			procName = ParamUtils.getProcName(opp.getClass());
			if (procName == null || "".equals(procName))
				throw new OpaStructureException("OpenEdge procedure name must be provided");
		}

		long startTs = System.currentTimeMillis();

		String requestId = generateRequestId(requestIdProvider);

		RqContext rqcontext = null;
		if (metaschema != null) {
			rqcontext = runProcedure(requestId, procName, paramSet, metaschema);
		} else {
			rqcontext = runProcedure(requestId, procName, paramSet);
		}

		if (System.currentTimeMillis() - startTs > 5000)
			logger.debug("Opa call to proc '{}' took {}ms", procName, System.currentTimeMillis() - startTs);

		// assign results (ordinary params) to bean; return rsmap - list of resultSets, which will be filled later
		//
		Map<Field, ResultSetHolder> rsmap = null;
		try {
			rsmap = ParamUtils.paramToBean(paramSet, opp);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new OpaStructureException("Error while assigning params to bean", e);
		}

		// results
		// according generated javaProxy, we require to setRqContext for last OUT/INOUT resultSet
		if (rqcontext != null) {
			if (!rqcontext._isStreaming()) {
				rqcontext._release();
			} else {
				// last
				ResultSetHolder lastRsh = null;
				for (Field f : rsmap.keySet()) {
					lastRsh = rsmap.get(f);
				}
				ResultSet resultset = null;
				if (lastRsh != null)
					resultset = (ResultSet) lastRsh.getResultSetValue();
				if (resultset != null)
					resultset.setRqContext(rqcontext);
			}
		}

		String returnVal = (String) paramSet.getProcedureReturnValue();

		// fill all lists from resultSet (from rsmap)
		//
		TableUtils.copyAllRecordSetsToBean(rsmap, opp);

		return returnVal;
	}

	public void setRequestIdGenerator(Supplier<String> requestIdGenerator) {
		this.requestIdGenerator = requestIdGenerator;
	}

	private String generateRequestId(Supplier<String> runReqIdProvider) {
		String requestId = null;
		Supplier<String> idProvider = runReqIdProvider != null ? runReqIdProvider : requestIdGenerator;
		if (idProvider != null) {
			try {
				requestId = idProvider.get();
			} catch (Throwable e) {
				requestId = newRequestID(this.m_properties);
				logger.warn("Cannot generate requestId, use generated by OE ({}). Exception: {}", requestId, e.getMessage());
			}
		}
		if (requestId == null)
			requestId = newRequestID(this.m_properties); // from oe lib

		return requestId;
	}
}
