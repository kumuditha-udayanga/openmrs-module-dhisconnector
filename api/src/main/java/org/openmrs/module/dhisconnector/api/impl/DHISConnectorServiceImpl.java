/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.dhisconnector.api.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.dhisconnector.api.DHISConnectorService;
import org.openmrs.module.dhisconnector.api.model.DHISDataValueSet;
import org.openmrs.module.dhisconnector.api.model.DHISImportSummary;
import org.openmrs.module.dhisconnector.api.model.DHISMapping;
import org.openmrs.module.dhisconnector.api.model.DHISOrganisationUnit;
import org.openmrs.module.reporting.report.definition.PeriodIndicatorReportDefinition;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.web.multipart.MultipartFile;

/**
 * It is a default implementation of {@link DHISConnectorService}.
 */
public class DHISConnectorServiceImpl extends BaseOpenmrsService implements DHISConnectorService {

	public static final String DHISCONNECTOR_MAPPINGS_FOLDER = File.separator + "dhisconnector" + File.separator
			+ "mappings";

	public static final String DHISCONNECTOR_CACHE_FOLDER = File.separator + "dhisconnector" + File.separator + "cache";

	public static final String DHISCONNECTOR_TEMP_FOLDER = File.separator + "dhisconnector" + File.separator + "temp";

	public static final String DHISCONNECTOR_MAPPING_FILE_SUFFIX = ".mapping.json";

	public static final String DHISCONNECTOR_ORGUNIT_RESOURCE = "/api/organisationUnits.json?paging=false";

	public static final String DATASETS_PATH = "/api/dataValueSets";

	private String getFromCache(String path) {
		String cacheFilePath = OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_CACHE_FOLDER + path;

		File cacheFile = new File(cacheFilePath);

		if (cacheFile.exists()) {
			try {
				return FileUtils.readFileToString(cacheFile);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		return null;
	}

	// TODO: error handling
	private void saveToCache(String path, String jsonResponse) {
		String cacheDirecoryPath = OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_CACHE_FOLDER;

		File cacheDirecory = new File(cacheDirecoryPath);

		if (!cacheDirecory.exists()) {
			try {
				if (!cacheDirecory.mkdirs()) {
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}

		String directoryStructure = OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_CACHE_FOLDER
				+ path.substring(0, path.lastIndexOf(File.separator));

		File directory = new File(directoryStructure);

		if (!directory.exists()) {
			try {
				if (!directory.mkdirs()) {
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}

		try {
			PrintWriter enpointCache = new PrintWriter(
					OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_CACHE_FOLDER + path, "utf-8");
			enpointCache.write(jsonResponse);
			enpointCache.close();
		} catch (Exception e) {
			return;
		}

		return;
	}

	@Override
	public String getDataFromDHISEndpoint(String endpoint) {
		String url = Context.getAdministrationService().getGlobalProperty("dhisconnector.url");
		String user = Context.getAdministrationService().getGlobalProperty("dhisconnector.user");
		String pass = Context.getAdministrationService().getGlobalProperty("dhisconnector.pass");

		DefaultHttpClient client = null;
		String payload = "";

		try {
			URL dhisURL = new URL(url);

			String host = dhisURL.getHost();
			int port = dhisURL.getPort();

			HttpHost targetHost = new HttpHost(host, port, dhisURL.getProtocol());
			client = new DefaultHttpClient();
			BasicHttpContext localcontext = new BasicHttpContext();

			HttpGet httpGet = new HttpGet(dhisURL.getPath() + endpoint);
			Credentials creds = new UsernamePasswordCredentials(user, pass);
			Header bs = new BasicScheme().authenticate(creds, httpGet, localcontext);
			httpGet.addHeader("Authorization", bs.getValue());
			httpGet.addHeader("Content-Type", "application/json");
			httpGet.addHeader("Accept", "application/json");
			HttpResponse response = client.execute(targetHost, httpGet, localcontext);
			HttpEntity entity = response.getEntity();

			if (response.getStatusLine().getStatusCode() != 200) {
				// TODO: Handle this
			}

			if (entity != null) {
				payload = EntityUtils.toString(entity);

				saveToCache(endpoint, payload);
			} else {
				// load from cache
				payload = getFromCache(endpoint);
			}
			// TODO: fix these catches ...
		} catch (Exception ex) {
			ex.printStackTrace();

			payload = getFromCache(endpoint);
		} finally {
			if (client != null) {
				client.getConnectionManager().shutdown();
			}
		}

		return payload;
	}

	@Override
	public String postDataToDHISEndpoint(String endpoint, String jsonPayload) {
		String url = Context.getAdministrationService().getGlobalProperty("dhisconnector.url");
		String user = Context.getAdministrationService().getGlobalProperty("dhisconnector.user");
		String pass = Context.getAdministrationService().getGlobalProperty("dhisconnector.pass");

		DefaultHttpClient client = null;
		String payload = "";

		try {

			URL dhisURL = new URL(url);

			String host = dhisURL.getHost();
			int port = dhisURL.getPort();

			HttpHost targetHost = new HttpHost(host, port, dhisURL.getProtocol());
			client = new DefaultHttpClient();
			BasicHttpContext localcontext = new BasicHttpContext();

			HttpPost httpPost = new HttpPost(dhisURL.getPath() + endpoint);

			Credentials creds = new UsernamePasswordCredentials(user, pass);
			Header bs = new BasicScheme().authenticate(creds, httpPost, localcontext);
			httpPost.addHeader("Authorization", bs.getValue());
			httpPost.addHeader("Content-Type", "application/json");
			httpPost.addHeader("Accept", "application/json");

			httpPost.setEntity(new StringEntity(jsonPayload));

			HttpResponse response = client.execute(targetHost, httpPost, localcontext);
			HttpEntity entity = response.getEntity();

			if (entity != null) {
				payload = EntityUtils.toString(entity);
			} else {
				// TODO: figure out what to do here
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (client != null) {
				client.getConnectionManager().shutdown();
			}
		}

		return payload;
	}

	@Override
	public boolean testDHISServerDetails(String url, String user, String pass) {
		URL testURL;
		Boolean success = true;

		// Check if the URL makes sense
		try {
			testURL = new URL(url + "/api/resources"); // Add the root API
														// endpoint to the URL
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return false;
		}

		HttpHost targetHost = new HttpHost(testURL.getHost(), testURL.getPort(), testURL.getProtocol());
		DefaultHttpClient httpclient = new DefaultHttpClient();
		BasicHttpContext localcontext = new BasicHttpContext();

		try {
			HttpGet httpGet = new HttpGet(testURL.toURI());
			Credentials creds = new UsernamePasswordCredentials(user, pass);
			Header bs = new BasicScheme().authenticate(creds, httpGet, localcontext);
			httpGet.addHeader("Authorization", bs.getValue());
			httpGet.addHeader("Content-Type", "application/json");
			httpGet.addHeader("Accept", "application/json");

			HttpResponse response = httpclient.execute(targetHost, httpGet, localcontext); // Execute
																							// the
																							// test
																							// query

			if (response.getStatusLine().getStatusCode() != 200) {
				success = false;

			}
		} catch (Exception ex) {
			ex.printStackTrace();
			success = false;
		} finally {
			httpclient.getConnectionManager().shutdown();
		}

		return success;
	}

	@Override
	public Object saveMapping(DHISMapping mapping) {
		String mappingsDirecoryPath = OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_MAPPINGS_FOLDER;

		File mappingsDirecory = new File(mappingsDirecoryPath);

		if (!mappingsDirecory.exists()) {
			try {
				if (!mappingsDirecory.mkdirs()) {
					return null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				return e;
			}
		}

		String filename = mapping.getName() + "." + mapping.getCreated() + ".mapping.json";

		File newMappingFile = new File(mappingsDirecoryPath + File.separator + filename);

		ObjectMapper mapper = new ObjectMapper();

		try {
			mapper.writeValue(newMappingFile, mapping);
		} catch (Exception e) {
			e.printStackTrace();
			return e;
		}

		return mapping;
	}

	@Override
	public DHISImportSummary postDataValueSet(DHISDataValueSet dataValueSet) {
		ObjectMapper mapper = new ObjectMapper();
		String jsonString;
		DHISImportSummary response;

		try {
			jsonString = mapper.writeValueAsString(dataValueSet);

			String responseString = postDataToDHISEndpoint(DATASETS_PATH, jsonString);

			response = mapper.readValue(responseString, DHISImportSummary.class);
		} catch (Exception e) {
			return null;
		}

		return response;
	}

	@Override
	public List<DHISMapping> getMappings() {
		List<DHISMapping> mappings = new ArrayList<DHISMapping>();

		ObjectMapper mapper = new ObjectMapper();

		String mappingsDirecoryPath = OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_MAPPINGS_FOLDER;

		File mappingsDirecory = new File(mappingsDirecoryPath);

		File[] files = mappingsDirecory.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(DHISCONNECTOR_MAPPING_FILE_SUFFIX);
			}
		});

		if (files == null)
			return null;

		for (File f : files) {
			try {
				mappings.add(mapper.readValue(f, DHISMapping.class));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return mappings;
	}

	@Override
	public List<PeriodIndicatorReportDefinition> getReportWithMappings(List<DHISMapping> mappings) {
		List<ReportDefinition> all = Context.getService(ReportDefinitionService.class).getAllDefinitions(false);

		List<PeriodIndicatorReportDefinition> pireports = new ArrayList<PeriodIndicatorReportDefinition>();

		for (ReportDefinition r : all) {
			if (r instanceof PeriodIndicatorReportDefinition && mappingsHasGUID(mappings, r.getUuid())) {
				pireports.add((PeriodIndicatorReportDefinition) r);
			}
		}

		return pireports;
	}

	@Override
	public List<DHISOrganisationUnit> getDHISOrgUnits() {
		List<DHISOrganisationUnit> orgUnits = new ArrayList<DHISOrganisationUnit>();

		ObjectMapper mapper = new ObjectMapper();
		String jsonResponse = new String();
		JsonNode node;

		jsonResponse = Context.getService(DHISConnectorService.class)
				.getDataFromDHISEndpoint(DHISCONNECTOR_ORGUNIT_RESOURCE);

		try {
			node = mapper.readTree(jsonResponse);
			orgUnits = Arrays
					.asList(mapper.readValue(node.get("organisationUnits").toString(), DHISOrganisationUnit[].class));
		} catch (Exception ex) {
			System.out.print(ex.getMessage());
		}

		return orgUnits;
	}

	private boolean mappingsHasGUID(List<DHISMapping> mappings, String GUID) {
		if (mappings == null)
			return false;

		for (DHISMapping mapping : mappings) {
			if (mapping.getPeriodIndicatorReportGUID().equals(GUID)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public String uploadMappings(MultipartFile mapping) {
		String msg = "";
		String tempFolderName = OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_TEMP_FOLDER + File.separator;
		String mappingFolderName = OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_MAPPINGS_FOLDER
				+ File.separator;
		String mappingName = mapping.getOriginalFilename();

		if (mappingName.endsWith(".zip")) {
			boolean allFailed = true;
			File tempMappings = new File(tempFolderName + mappingName);

			(new File(tempFolderName)).mkdirs();
			try {
				mapping.transferTo(tempMappings);

				try {
					ZipFile zipfile = new ZipFile(tempMappings);

					for (Enumeration e = zipfile.entries(); e.hasMoreElements();) {
						ZipEntry entry = (ZipEntry) e.nextElement();

						if (entry.isDirectory()) {
							System.out.println("Incorrect file (Can't be a folder instead): " + entry.getName()
									+ " has been ignored");
						} else if (entry.getName().endsWith(DHISCONNECTOR_MAPPING_FILE_SUFFIX)) {
							File outputFile = new File(mappingFolderName, entry.getName());

							if (outputFile.exists()) {
								System.out.println(
										"File: " + outputFile.getName() + " already exists and has been ignored");
							} else {
								BufferedInputStream inputStream = new BufferedInputStream(
										zipfile.getInputStream(entry));
								BufferedOutputStream outputStream = new BufferedOutputStream(
										new FileOutputStream(outputFile));

								try {
									System.out.println("Extracting: " + entry);
									IOUtils.copy(inputStream, outputStream);
									allFailed = false;
								} finally {
									outputStream.close();
									inputStream.close();
								}
							}
						} else {
							System.out.println("Incorrect file: " + entry.getName() + " has been ignored");
						}
					}
					if (!allFailed) {
						msg = Context.getMessageSourceService().getMessage("dhisconnector.uploadMapping.groupSuccess");
					} else {
						msg = Context.getMessageSourceService().getMessage("dhisconnector.uploadMapping.allFailed");
					}
					FileUtils.deleteDirectory(new File(tempFolderName));
				} catch (Exception e) {
					System.out.println("Error while extracting file:" + mapping.getName() + " ; " + e);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (mappingName.endsWith(DHISCONNECTOR_MAPPING_FILE_SUFFIX)) {
			try {
				File uploadedMapping = new File(mappingFolderName + mappingName);
				if (uploadedMapping.exists()) {
					msg = Context.getMessageSourceService().getMessage("dhisconnector.uploadMapping.exists");
				} else {
					mapping.transferTo(uploadedMapping);
					msg = Context.getMessageSourceService().getMessage("dhisconnector.uploadMapping.singleSuccess");
				}

			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			msg = Context.getMessageSourceService().getMessage("dhisconnector.uploadMapping.wrongType");
		}

		return msg;
	}

	@Override
	public String[] exportSelectedMappings(String[] selectedMappings) {
		String msg = "";
		String[] returnStr = new String[2];
		String path = null;

		// TODO remove last item from selectedMappings
		try {
			byte[] buffer = new byte[1024];
			String sourceDirectory = OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_MAPPINGS_FOLDER
					+ File.separator;
			String tempFolderName = OpenmrsUtil.getApplicationDataDirectory() + DHISCONNECTOR_TEMP_FOLDER
					+ File.separator;
			String suffix = ".mapping.json";
			String zipFile = tempFolderName + "exported-mappings_" + (new Date()).getTime() + ".zip";

			(new File(tempFolderName)).mkdirs();

			FileOutputStream fout = new FileOutputStream(zipFile);
			ZipOutputStream zout = new ZipOutputStream(fout);
			File dir = new File(sourceDirectory);

			if (!dir.isDirectory()) {
				System.out.println(sourceDirectory + " is not a directory");
			} else {
				File[] files = dir.listFiles();
				String mappings = "";

				if (files.length == 0) {
					msg = Context.getMessageSourceService().getMessage("dhisconnector.exportMapping.noMappingsFound");
				} else {
					for (int i = 0; i < files.length; i++) {
						if (files[i].getName().endsWith(suffix)) {
							FileInputStream fin = new FileInputStream(files[i]);

							mappings += files[i].getName() + "<:::>";
							System.out.println("Compressing " + files[i].getName());
							if (selectedMappings.length == 0) {
								copyToZip(buffer, zout, files, i, fin);
							} else {
								if (arrayIncludes(selectedMappings, files[i].getName())) {
									copyToZip(buffer, zout, files, i, fin);
								}
							}
							msg = Context.getMessageSourceService().getMessage("dhisconnector.exportMapping.success");
							zout.closeEntry();
							fin.close();
						}
					}
					if(mappings.split("<:::>").length == 0) {
						msg = Context.getMessageSourceService().getMessage("dhisconnector.exportMapping.noMappingsFound");
					}
					path = zipFile;
				}
			}
			zout.close();
			System.out.println("Zip file has been created!");
		} catch (IOException e) {
			e.printStackTrace();
		}
		returnStr[0] = msg;
		returnStr[1] = path;
		return returnStr;
	}

	private void copyToZip(byte[] buffer, ZipOutputStream zout, File[] files, int i, FileInputStream fin)
			throws IOException {
		zout.putNextEntry(new ZipEntry(files[i].getName()));
		int length;
		while ((length = fin.read(buffer)) > 0) {
			zout.write(buffer, 0, length);
		}
	}

	private boolean arrayIncludes(String[] selectedMappings, String name) {
		boolean contains = false;

		for (int i = 0; i < selectedMappings.length; i++) {
			if ((selectedMappings[i] + DHISCONNECTOR_MAPPING_FILE_SUFFIX).equals(name)) {
				contains = true;
			}
		}
		return contains;
	}
}
