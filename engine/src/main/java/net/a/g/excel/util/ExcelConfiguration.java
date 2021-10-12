package net.a.g.excel.util;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Named
public class ExcelConfiguration {

	public final static Logger LOG = LoggerFactory.getLogger(ExcelConfiguration.class);

	enum EXCELRETURN {
		MAP, LIST
	}

	@ConfigProperty(name = ExcelConstants.EXCEL_STATIC_RESOURCE_URI, defaultValue = ExcelConstants.DOT)
	String resouceUri;
	@ConfigProperty(name = ExcelConstants.EXCEL_STATIC_READONLY, defaultValue = ExcelConstants.TRUE)
	boolean readOnly;
	@ConfigProperty(name = ExcelConstants.EXCEL_LIST_MAP, defaultValue = ExcelConstants.EXCEL_RETURN_DEFAULT)
	EXCELRETURN retourFormat = EXCELRETURN.MAP;
	@ConfigProperty(name = ExcelConstants.EXCEL_FORMAT_DATE, defaultValue = ExcelConstants.FORMAT_DATE_ISO)
	String formatDate = ExcelConstants.FORMAT_DATE_ISO;

	public EXCELRETURN getFormatRetour() {
		return retourFormat;
	}

	public String getFormatDate() {
		return formatDate;
	}

	public String getResouceUri() {
		return resouceUri;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public boolean returnList() {
		return retourFormat == EXCELRETURN.LIST;
	}

	public boolean returnMap() {
		return retourFormat == EXCELRETURN.MAP;
	}

	

	@Override
	public String toString() {
		return "ExcelConfiguration [resouceUri=" + resouceUri + ", readOnly=" + readOnly + ", retourFormat="
				+ retourFormat + ", formatDate=" + formatDate + "]";
	}

	@PostConstruct
	public void postConstruc() {
		LOG.info(this.toString());
	}

}
