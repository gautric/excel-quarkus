package net.a.g.excel.rest;

import static net.a.g.excel.rest.ExcelConstants.API;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.openapi.annotations.ExternalDocumentation;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.a.g.excel.engine.ExcelEngine;
import net.a.g.excel.model.ExcelCell;
import net.a.g.excel.model.ExcelLink;
import net.a.g.excel.model.ExcelModel;
import net.a.g.excel.model.ExcelResource;
import net.a.g.excel.model.ExcelResult;
import net.a.g.excel.model.ExcelSheet;
import net.a.g.excel.util.ExcelConfiguration;
import net.a.g.excel.util.ExcelConstants;

@Path(API)
@OpenAPIDefinition(externalDocs = @ExternalDocumentation(description = "schema", url = ExcelConstants.SCHEMA_URI), info = @Info(version = "1.0", title = "Excel Quarkus"))
public class ExcelRestResource {

	public final static Logger LOG = LoggerFactory.getLogger(ExcelRestResource.class);

	@Inject
	ExcelConfiguration conf;

	@Inject
	ExcelEngine engine;

	@Context
	UriInfo uriInfo;

	@POST
	@Path("{resource}/{sheet}/{cells}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@APIResponses(value = {
			@APIResponse(responseCode = "404", description = "if {resource} or {sheet} is not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))),
			@APIResponse(responseCode = "200", description = "Nominal result, return ExcelResult + ExcelCell[]", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))) })
	@Operation(summary = "List of Excel Cell computed", description = "Retrieves and returns the list of Excel Cell")
	public Response cellBody(@PathParam("resource") String resource, @PathParam("sheet") String sheetName,
			@PathParam("cells") String cellNames, @QueryParam("_global") @DefaultValue("false") boolean global,
			final String jsonBody) {
		JSONObject body = new JSONObject(jsonBody);

		Map<String, List<String>> query = body.toMap().entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> Arrays.asList(e.getValue().toString())));

		return computeCells(resource, sheetName, cellNames, global, query);
	}

	@POST
	@Path("{resource}/{sheet}/{cells}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@APIResponses(value = {
			@APIResponse(responseCode = "404", description = "if {resource} or {sheet} is not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))),
			@APIResponse(responseCode = "200", description = "Nominal result, return ExcelResult + ExcelCell[]", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))) })
	@Operation(summary = "List of Excel Cell computed", description = "Retrieves and returns the list of Excel Cell")
	public Response cellForm(@PathParam("resource") String resource, @PathParam("sheet") String sheetName,
			@PathParam("cells") String cellNames, @QueryParam("_global") @DefaultValue("false") boolean global,
			final MultivaluedMap<String, String> queryurlencoded) {

		Map<String, List<String>> query = queryurlencoded;

		return computeCells(resource, sheetName, cellNames, global, query);
	}

	@GET
	@Path("{resource}/{sheet}/{cells}")
	@Produces(MediaType.APPLICATION_JSON)
	@APIResponses(value = {
			@APIResponse(responseCode = "404", description = "if {resource} or {sheet} is not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))),
			@APIResponse(responseCode = "200", description = "Nominal result, return ExcelResult + ExcelCell[]", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))) })
	@Operation(summary = "List of Excel Cell computed", description = "Retrieves and returns the list of Excel Cell")
	public Response cellQuery(@PathParam("resource") String resource, @PathParam("sheet") String sheetName,
			@PathParam("cells") String cellNames, @QueryParam("_global") @DefaultValue("false") boolean global) {

		Map<String, List<String>> query = uriInfo.getQueryParameters();

		return computeCells(resource, sheetName, cellNames, global, query);
	}

	private Response computeCells(String resource, String sheetName, String input, boolean global,
			Map<String, List<String>> query) {
		Link link = Link.fromUri(uriInfo.getRequestUri()).rel("self").build();
		UriBuilder builder = UriBuilder.fromUri(uriInfo.getBaseUri() + API).path(ExcelRestResource.class, "cellQuery");

		if (!getEngine().isResourceExists(resource)) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}

		if (!getEngine().isSheetExists(resource, sheetName)) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}

		List<ExcelCell> entity = getEngine().cellCalculationOld(resource, sheetName, Arrays.asList(input.split(",")),
				query, global);

		entity.forEach(e -> injectLink(
				() -> new String[] { resource, e.getAddress().split("!")[0], e.getAddress().split("!")[1] }, builder)
						.accept(e));

		ExcelResult ret = new ExcelResult(entity.size(), entity);

		return ExcelRestTool.returnOK(ret, link);
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@APIResponses(value = {
			@APIResponse(responseCode = "200", description = "Nominal result, return ExcelResult + ExcelResource[]", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))) })
	@Operation(summary = "List of Excel Resources", description = "Retrieves and returns the list of Excel Resources")
	public Response resources() throws Exception {

		Link link = Link.fromUri(uriInfo.getRequestUri()).rel("self").build();
		UriBuilder builder = UriBuilder.fromUri(uriInfo.getRequestUri()).path(ExcelRestResource.class, "sheets");

		Collection<ExcelResource> entity = getEngine().lisfOfResource();

		entity.forEach(e -> e.getLinks().clear());

		entity.forEach(e -> injectLink(() -> new String[] { e.getName() }, builder).accept(e));

		ExcelResult ret = new ExcelResult(getEngine().countListOfResource(), entity);

		return ExcelRestTool.returnOK(ret, link);
	}

	@GET
	@Path("{resource}")
	@Produces(MediaType.APPLICATION_JSON)
	@APIResponses(value = {
			@APIResponse(responseCode = "404", description = "if {resource} is not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))),
			@APIResponse(responseCode = "200", description = "Nominal result, return ExcelResult + ExcelSheet[]", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))) })
	@Operation(summary = "List of Excel Sheets", description = "Retrieves and returns the list of Excel Sheets")
	public Response sheets(@PathParam("resource") String resource) {
		Link link = Link.fromUri(uriInfo.getRequestUri()).rel("self").build();

		UriBuilder builder = uriInfo.getAbsolutePathBuilder().path(ExcelRestResource.class, "sheets");

		if (!getEngine().isResourceExists(resource)) {
			return Response.status(Response.Status.NOT_FOUND).links(link).build();
		}

		List<ExcelSheet> listOfSheet = getEngine().listOfSheet(resource);

		listOfSheet.forEach(s -> injectLink(() -> new String[] { s.getName() }, builder).accept(s));

		ExcelResult ret = new ExcelResult(listOfSheet.size(), listOfSheet);

		return ExcelRestTool.returnOK(ret, link);
	}

	private Consumer<? super ExcelModel> injectLink(Supplier<String[]> supply, UriBuilder builder) {
		return cell -> {
			ExcelLink el = new ExcelLink();
			el.setHref(builder.build(supply.get()).toString());
			el.setRel("self");
			el.setType(MediaType.APPLICATION_JSON);
			cell.getLinks().add(el);
		};
	}

	@POST
	@Path("/{resource}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	@APIResponses(value = {
			@APIResponse(responseCode = "400", description = "Resource uploaded is not Excel file", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))),
			@APIResponse(responseCode = "202", description = "Resource is accepted", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))) })
	@Operation(summary = "Create a new Excel Resource", description = "Create a new Excel Resource")
	public Response uploadFile(@PathParam("resource") String resource, MultipartFormDataInput input)
			throws IOException {

		String fileName = "";
		byte[] bytes = null;

		Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
		List<InputPart> inputParts = uploadForm.get("file");

		for (InputPart inputPart : inputParts) {

			MultivaluedMap<String, String> header = inputPart.getHeaders();
			fileName = getFileName(header);

			InputStream inputStream = inputPart.getBody(InputStream.class, null);

			bytes = IOUtils.toByteArray(inputStream);
		}

		ExcelResource excelResource = new ExcelResource();
		excelResource.setName(resource);
		excelResource.setFile(fileName);
		excelResource.setDoc(bytes);

		if (!getEngine().addResource(excelResource)) {
			return ExcelRestTool.returnKO(Response.Status.BAD_REQUEST,
					"Server cannot accept/recognize format file provided");
		}

		String url = uriInfo.getRequestUri().toString();

		ExcelResult ret = new ExcelResult();
		ret.setSelf(url);
		ret.setResults(excelResource);

		return Response.accepted(ret).build();
	}

	/**
	 * header sample { Content-Type=[image/png], Content-Disposition=[form-data;
	 * name="file"; filename="filename.extension"] }
	 **/
	// get uploaded filename, is there a easy way in RESTEasy?
	private String getFileName(MultivaluedMap<String, String> header) {

		String[] contentDisposition = header.getFirst("Content-Disposition").split(";");

		for (String filename : contentDisposition) {
			if ((filename.trim().startsWith("filename"))) {

				String[] name = filename.split("=");

				String finalFileName = name[1].trim().replaceAll("\"", "");
				return finalFileName;
			}
		}
		return "unknown";
	}

	@GET
	@Path("{resource}/{sheet}")
	@Produces(MediaType.APPLICATION_JSON)
	@APIResponses(value = {
			@APIResponse(responseCode = "404", description = "if {resource} or {sheet} is not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))),
			@APIResponse(responseCode = "200", description = "Nominal result, return ExcelResult + Cell[]", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExcelResult.class))) })
	@Operation(summary = "List of Excel Cells", description = "Retrieves and returns the list of Excel Cell")
	public Response sheet(@PathParam("resource") String resource, @PathParam("sheet") String sheetName) {
		Link link = Link.fromUri(uriInfo.getRequestUri()).rel("self").build();
		UriBuilder builder = UriBuilder.fromUri(uriInfo.getBaseUri() + API).path(ExcelRestResource.class, "cellQuery");

		Response.Status status = Response.Status.NOT_FOUND;
		List<ExcelCell> entity = null;
		ExcelResult ret = null;

		if (!getEngine().isResourceExists(resource)) {
			return Response.status(Response.Status.NOT_FOUND).links(link).build();
		}

		if (!getEngine().isSheetExists(resource, sheetName)) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}

		if (!sheetName.contains("!")) {
			if (getEngine().isSheetExists(resource, sheetName)) {
				status = Response.Status.OK;

				entity = getEngine().listOfCell(resource, sheetName, cell -> true);
				ret = new ExcelResult(entity.size(), entity);

				entity.forEach(e -> injectLink(
						() -> new String[] { resource, e.getAddress().split("!")[0], e.getAddress().split("!")[1] },
						builder).accept(e));

			} else {
				return Response.status(status).build();
			}
		}
		return ExcelRestTool.returnOK(ret, link);
	}

	public ExcelConfiguration getConf() {
		return conf;
	}

	public ExcelEngine getEngine() {
		return engine;
	}

	public UriInfo getUriInfo() {
		return uriInfo;
	}

}
