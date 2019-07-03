package business.generator.impl.generators;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import annotation.ConsumesAPI;
import business.converter.InfoObjectConverter;
import business.generator.impl.connectors.ServiceConnector;
import business.model.Dependency;
import business.model.SystemDescriptionModel;
import collectors.models.InfoObject;
import collectors.models.maven.CollectedMavenInfoObject;
import collectors.models.maven.ComponentInfoObject;
import collectors.models.maven.ModuleInfoObject;
import collectors.models.maven.ModuleToComponentInfoObject;
import collectors.models.restapi.CollectedAPIInfoObject;
import data.file.FileReader;
import data.file.PlantUMLDiagramWriter;
import data.interfaces.DataOutputToFile;
import mojos.DocumentationMojo;
import net.sourceforge.plantuml.Log;

/**
 * Creates diagrams based on available information in the source folders. The
 * diagrams are created via PlantUML and GraphViz. GraphViz has to be installed
 * on the system, or the created svg files are pictures/text of error messages.
 * If the diagrams are saved into files, they will be created in subfolders of
 * the target folder: svg files in subfolder .\svg and the PlantUml description
 * in subfolder .\txt.
 * 
 * 
 * @author gmittmann
 *
 */
public class PlantUMLDiagramGenerator {

	private static String BEGIN_DIAGRAM = "@startuml\n skinparam componentStyle uml2\n\n";
	private static String END_DIAGRAM = "@enduml\n";

	public List<File> generateDocuments(File targetFolder, boolean visualize, DataOutputToFile output,
			File... srcFolders) {
		List<File> diagramFiles = new ArrayList<>();

		List<CollectedMavenInfoObject> infoObjects = getCollectedInfoObjects(
				DocumentationMojo.MAVEN_AGGREGATE_NAME + DocumentationMojo.SUFFIX, CollectedMavenInfoObject.class,
				srcFolders);
		List<CollectedAPIInfoObject> apiInfoObjects = getCollectedInfoObjects(
				DocumentationMojo.API_AGGREGATE_NAME + DocumentationMojo.SUFFIX, CollectedAPIInfoObject.class,
				srcFolders);

		List<Dependency> serviceDependencies = null;
		if (apiInfoObjects != null && !apiInfoObjects.isEmpty()) {
			ServiceConnector connector = new ServiceConnector();
			serviceDependencies = connector.connectServices(apiInfoObjects);
			System.out.println("FOUND " + serviceDependencies.size() + " DEPENDENCIES");
		}

		// Create diagrams
		Map<String, String> modules = createModuleDiagram(infoObjects, serviceDependencies);
		for (Entry<String, String> moduleEntry : modules.entrySet()) {
			diagramFiles.addAll(output.writeToFile(moduleEntry.getValue(), moduleEntry.getKey().split("\\.")[0],
					moduleEntry.getKey().split("\\.")[1], targetFolder));
		}
		Map<String, String> components = createComponentDiagram(infoObjects, serviceDependencies);
		for (Entry<String, String> componentEntry : components.entrySet()) {
			diagramFiles.addAll(output.writeToFile(componentEntry.getValue(), componentEntry.getKey().split("\\.")[0],
					componentEntry.getKey().split("\\.")[1], targetFolder));
		}
		Map<String, String> systems = createSystemDiagram(infoObjects);
		for (Entry<String, String> systemsEntry : systems.entrySet()) {
			diagramFiles.addAll(output.writeToFile(systemsEntry.getValue(), systemsEntry.getKey().split("\\.")[0],
					systemsEntry.getKey().split("\\.")[1], targetFolder));
		}
		Map<String, String> service = createServiceDiagram(infoObjects, serviceDependencies);
		for (Entry<String, String> serviceEntry : service.entrySet()) {
			diagramFiles.addAll(output.writeToFile(serviceEntry.getValue(), serviceEntry.getKey().split("\\.")[0],
					serviceEntry.getKey().split("\\.")[1], targetFolder));
		}

		if (visualize) {
			PlantUMLDiagramWriter umlWriter = new PlantUMLDiagramWriter();
			diagramFiles.addAll(umlWriter.generateSVGFile(modules, targetFolder));
			diagramFiles.addAll(umlWriter.generateSVGFile(components, targetFolder));
			diagramFiles.addAll(umlWriter.generateSVGFile(systems, targetFolder));
			diagramFiles.addAll(umlWriter.generateSVGFile(service, targetFolder));
		}

		return diagramFiles;
	}

	public Map<String, String> generateDocuments(boolean visualize, File... srcFolders) {
		Map<String, String> diagramStrings = new HashMap<>();

		List<CollectedMavenInfoObject> infoObjects = getCollectedInfoObjects(
				DocumentationMojo.MAVEN_AGGREGATE_NAME + DocumentationMojo.SUFFIX, CollectedMavenInfoObject.class,
				srcFolders);
		List<CollectedAPIInfoObject> apiInfoObjects = getCollectedInfoObjects(
				DocumentationMojo.API_AGGREGATE_NAME + DocumentationMojo.SUFFIX, CollectedAPIInfoObject.class,
				srcFolders);

		List<Dependency> serviceDependencies = null;
		if (apiInfoObjects != null && !apiInfoObjects.isEmpty()) {
			ServiceConnector connector = new ServiceConnector();
			serviceDependencies = connector.connectServices(apiInfoObjects);
			System.out.println("FOUND " + serviceDependencies.size() + " DEPENDENCIES");
		}

		// Create diagrams
		Map<String, String> modules = createModuleDiagram(infoObjects, serviceDependencies);
		diagramStrings.putAll(modules);

		Map<String, String> components = createComponentDiagram(infoObjects, serviceDependencies);
		diagramStrings.putAll(components);

		Map<String, String> systems = createSystemDiagram(infoObjects);
		diagramStrings.putAll(systems);

		Map<String, String> service = createServiceDiagram(infoObjects, serviceDependencies);
		diagramStrings.putAll(service);

		if (visualize) {
			PlantUMLDiagramWriter umlWriter = new PlantUMLDiagramWriter();
			diagramStrings.putAll(umlWriter.generateSVGString(diagramStrings));
		}

		return diagramStrings;
	}

	private <T extends InfoObject> List<T> getCollectedInfoObjects(String name, Class<T> clazz, File... srcFolders) {
		List<File> foundFiles = new ArrayList<>();
		for (File file : srcFolders) {
			foundFiles.addAll(FileReader.findFilesWithName(file, name, ".json"));
		}
		return InfoObjectConverter.createJSONObjects(foundFiles, clazz);
	}

	/**
	 * Creates package diagrams describing the dependencies between modules.
	 * 
	 * @param infoObjects List of InfoObjects based on whom the diagrams are to be
	 *                    created.
	 * @return Map containing mapping from name of the file to content.
	 */
	public Map<String, String> createModuleDiagram(List<CollectedMavenInfoObject> infoObjects,
			List<Dependency> serviceDependencies) {
		System.out.println("---- creating diagrams for modules ----");
		Map<String, String> fileNameToContent = new HashMap<>();

		Map<String, String> umlDescriptions = new HashMap<>();
		for (CollectedMavenInfoObject currentInfo : infoObjects) {

			if (currentInfo.getModuleDependencies() != null) {
				String src = BEGIN_DIAGRAM;

				src += createModuleDiagramString(currentInfo);

				src += END_DIAGRAM;
				umlDescriptions.put(currentInfo.getTag(), src);
			} else {
				System.out.println("No info about module dependencies found for: " + currentInfo.getProjectName());
			}
		}

		for (Entry<String, String> descriptionEntry : umlDescriptions.entrySet()) {
			fileNameToContent.put(generateDiagramName(descriptionEntry, "modules") + ".txt",
					descriptionEntry.getValue());
		}

		String allModules = createAllInServicesDiagramString(umlDescriptions, infoObjects, serviceDependencies, false);
		fileNameToContent.put("all_modules.txt", allModules);

		return fileNameToContent;
	}

	/**
	 * Creates the String containing the actual PlantUML description of the diagram
	 * for the module dependencies.
	 * 
	 * @param infoObject CollectedMavenInfoObject containing the information about
	 *                   the modules.
	 * @return String containing the diagram description
	 */
	private String createModuleDiagramString(CollectedMavenInfoObject infoObject) {

		String diagramString = "";
		Map<String, List<String>> moduleDependencies = infoObject.getModuleDependencies();

		for (String module : moduleDependencies.keySet()) {
			diagramString += "package \"" + getModuleNameFromTag(infoObject, module) + "\" {}\n";
		}
		diagramString += "\n";

		for (Entry<String, List<String>> entry : moduleDependencies.entrySet()) {
			for (String dep : entry.getValue()) {
				diagramString += "\"" + getModuleNameFromTag(infoObject, entry.getKey()) + "\"" + " --> " + "\"" + getModuleNameFromTag(infoObject, dep) + "\"" + "\n";
			}
		}

		return diagramString;
	}
	
	private String getModuleNameFromTag(CollectedMavenInfoObject infoObject, String tag) {
		
		for(ModuleInfoObject moduleInfoObject : infoObject.getModules()) {
			if (moduleInfoObject.getTag().equalsIgnoreCase(tag)) {
				return moduleInfoObject.getModuleName();
			}
		}
		
		return tag;
	}

	/**
	 * Creates a package diagram describing the dependencies between components in
	 * their packages.
	 * 
	 * @param infoObjects List of InfoObjects based on whom the diagrams are to be
	 *                    created.
	 * @return Map containing mapping from name of the file to content.
	 */
	public Map<String, String> createComponentDiagram(List<CollectedMavenInfoObject> infoObjects,
			List<Dependency> serviceDependencies) {
		System.out.println("---- creating diagrams for components ----");

		Map<String, String> fileNameToContent = new HashMap<>();

		Map<String, String> umlDescriptions = new HashMap<>();
		for (CollectedMavenInfoObject currentInfo : infoObjects) {

			if (currentInfo.getModuleDependencies() != null) {
				String src = BEGIN_DIAGRAM;

				src += createComponentDiagramString(currentInfo);

				src += END_DIAGRAM;
				umlDescriptions.put(currentInfo.getTag(), src);
			} else {
				System.out.println("No info about module dependencies found for: " + currentInfo.getProjectName());
			}
		}

		String allDescriptions = createAllInServicesDiagramString(umlDescriptions, infoObjects, serviceDependencies,
				true);

		for (Entry<String, String> descriptionEntry : umlDescriptions.entrySet()) {
			fileNameToContent.put(generateDiagramName(descriptionEntry, "components") + ".txt",
					descriptionEntry.getValue());
		}

		fileNameToContent.put("all_components.txt", allDescriptions);

		return fileNameToContent;
	}

	private String createAllInServicesDiagramString(Map<String, String> umlDescriptions,
			List<CollectedMavenInfoObject> infoObjects, List<Dependency> serviceDependencies,
			boolean componentExternalDeps) {
		String description = BEGIN_DIAGRAM;

		for (Entry<String, String> currentEntry : umlDescriptions.entrySet()) {
			String innerPart = removePlantUMLPart(currentEntry.getValue());

			description += "package \"" + tagToServiceName(currentEntry.getKey(), infoObjects) + "\" { \n";
			description += innerPart;
			description += "}\n\n";

		}

		if (componentExternalDeps) {
			description += createExternalComponentDependenciesString(infoObjects, serviceDependencies);
		}
		description += END_DIAGRAM;

		return description;
	}

	private String removePlantUMLPart(String description) {
		return description.replace(BEGIN_DIAGRAM, "").replace(END_DIAGRAM, "");
	}

	/**
	 * Creates package diagrams describing the dependencies between components.
	 * 
	 * @param infoObjects  List of InfoObjects based on whom the diagrams are to be
	 *                     created.
	 * @param targetFolder Folder in which the subfolders and diagram files are
	 *                     written.
	 * @param visualize    if true, creates png and svg of the diagram, else just
	 *                     the textual description is created.
	 * @return List of created files.
	 */
	private String createComponentDiagramString(CollectedMavenInfoObject infoObject) {
		String diagramString = "";

		List<ModuleToComponentInfoObject> componentList = infoObject.getComponents();

		/* create packages & components */
		for (ModuleToComponentInfoObject moduleComponent : componentList) {

			diagramString += "package " + "\"" + moduleComponent.getModuleName() + "\" { \n";

			for (ComponentInfoObject info : moduleComponent.getComponents()) {
				diagramString += "[" + "\"" + info.getPackageName() + "\"] \n";
			}

			diagramString += "}\n\n";
		}

		diagramString += "\n";

		/* create dependencies between components */
		for (ModuleToComponentInfoObject moduleComponent : componentList) {
			for (ComponentInfoObject info : moduleComponent.getComponents()) {
				for (String dependency : info.getDependsOn()) {
					diagramString += "[\"" + info.getPackageName() + "\"]" + " ..> " + "[\"" + dependency
							+ "\"] : use \n";
				}

			}

			diagramString += "\n";
		}

		return diagramString;
	}

	private String createExternalComponentDependenciesString(List<CollectedMavenInfoObject> infoObjects,
			List<Dependency> serviceDependencies) {

		if (serviceDependencies == null || serviceDependencies.isEmpty()) {
			return "";
		}
		List<String> availableComponents = availableComponents(infoObjects);
		String dependencies = "";

		Map<String, String> addedDependencies = new HashMap<>();
		for (Dependency serviceDependency : serviceDependencies) {
			String dependencyPackageName = serviceDependency.getServicePackage();

			for (String component : availableComponents) {
				if (dependencyPackageName.startsWith(component)) {
					
					String dependOn = getComponentByPackage(availableComponents,
							serviceDependency.getDependsOnPackage());

					if (addedDependencies.get(component) == null || !addedDependencies.get(component).equals(dependOn)) {
						addedDependencies.put(component, dependOn);

						dependencies += "[\"" + component + "\"]" + " ..> " + "[\"" + dependOn + "\"] : call \n";
					}
				}
			}

		}

		return dependencies;
	}

	private String getLongest(List<String> allMatches) {
		String longest = "";
		for (String match : allMatches) {
			if (match.length() > longest.length()) {
				longest = match;
			}
		}
		return longest;
	}

	private String getComponentByPackage(List<String> availableComponents, String dependsOnPackage) {
		List<String> allMatches = new ArrayList<>();
		for (String component : availableComponents) {
			if (dependsOnPackage.startsWith(component)) {
				allMatches.add(component);
			}
		}
		if (!allMatches.isEmpty()) {
			return getLongest(allMatches);
		}
		return "EXTERN";
	}

	private List<String> availableComponents(List<CollectedMavenInfoObject> infoObjects) {
		List<String> names = new ArrayList<>();

		for (CollectedMavenInfoObject info : infoObjects) {
			for (ModuleToComponentInfoObject cInfo : info.getComponents()) {
				for (ComponentInfoObject pkgInfo : cInfo.getComponents()) {
					names.add(pkgInfo.getPackageName());
				}
			}
		}

		return names;
	}

	public Map<String, String> createSystemDiagram(List<CollectedMavenInfoObject> infoObjects) {
		System.out.println("---- creating system diagram ----");
		Map<String, String> fileNameToContent = new HashMap<>();

		Map<String, List<String>> sysToSubSys = new HashMap<>();
		for (CollectedMavenInfoObject currentInfo : infoObjects) {
			List<String> subSys = new ArrayList<>();
			subSys.add(currentInfo.getSubsystem());
			List<String> res = sysToSubSys.putIfAbsent(currentInfo.getSystem(), subSys);

			if (res != null) {
				subSys.addAll(res);
				sysToSubSys.put(currentInfo.getSystem(), subSys);
			}
		}

		String src = BEGIN_DIAGRAM;
		for (Entry<String, List<String>> entry : sysToSubSys.entrySet()) {
			src += createSystemDiagramString(entry);
		}
		src += END_DIAGRAM;

		fileNameToContent.put("systems" + ".txt", src);

		return fileNameToContent;

	}

	private String createSystemDiagramString(Entry<String, List<String>> entry) {
		String diagramDescription = "package \"" + entry.getKey() + "\" {\n";

		for (String subPackage : entry.getValue()) {
			diagramDescription += "package \"" + subPackage + "\" {}\n";
		}

		diagramDescription += "}\n\n";

		return diagramDescription;
	}

	public Map<String, String> createServiceDiagram(List<CollectedMavenInfoObject> infoObjects,
			List<Dependency> serviceDependencies) {
		System.out.println("---- creating diagrams for microservices in system ----");
		Map<String, String> fileNameToContent = new HashMap<>();

		List<SystemDescriptionModel> systems = new ArrayList<>();
		for (CollectedMavenInfoObject currentInfo : infoObjects) {
			SystemDescriptionModel sys = new SystemDescriptionModel(currentInfo.getSystem());
			if (systems.contains(sys)) {
				for (SystemDescriptionModel sdm : systems) {
					if (sys.equals(sdm)) {
						sys = sdm;
						break;
					}
				}
			} else {
				systems.add(sys);
			}

			sys.addSubsystem(currentInfo.getSubsystem(), currentInfo.getProjectName());

		}

		String src = BEGIN_DIAGRAM;
		for (SystemDescriptionModel model : systems) {
			src += createSystemMicroserviceDiagramString(model);
		}

		if (serviceDependencies != null) {
			for (Dependency dependency : serviceDependencies) {
				if (dependency.getDependsOn().equalsIgnoreCase("external")
						|| dependency.getDependsOn().equalsIgnoreCase(ConsumesAPI.DEFAULT_SERVICE)) {
					src += "package \"external\" {}\n";
					break;
				}
			}
			src += createServiceDependencies(serviceDependencies);
		}

		src += END_DIAGRAM;

		fileNameToContent.put("services" + ".txt", src);

		return fileNameToContent;

	}

	private String createSystemMicroserviceDiagramString(SystemDescriptionModel model) {
		String diagramDescription = "package \"" + model.getSystemName() + "\" {\n";
		for (Entry<String, List<String>> subPackage : model.getSubsysToMS().entrySet()) {
			diagramDescription += "package \"" + subPackage.getKey() + "\" {\n";
			for (String ms : subPackage.getValue()) {
				diagramDescription += "package \"" + ms + "\" {}\n";
			}

			diagramDescription += "}\n";
		}
		diagramDescription += "}\n\n";

		return diagramDescription;
	}

	private String createServiceDependencies(List<Dependency> serviceDependencies) {
		String diagramDescriptionServiceDependencies = "";

		if (serviceDependencies == null || serviceDependencies.isEmpty()) {
			Log.info("No dependencies between services found");
			return diagramDescriptionServiceDependencies;
		}

		for (Dependency dependency : serviceDependencies) {
			diagramDescriptionServiceDependencies += "\"" + dependency.getService() + "\"" + "-->" + "\""
					+ dependency.getDependsOn() + "\" : \"" + dependency.getMethod() + " : " + dependency.getPath()
					+ "\"\n";
		}

		return diagramDescriptionServiceDependencies;
	}

	private String generateDiagramName(Entry<String, String> descriptionEntry, String diagramType) {
		return descriptionEntry.getKey() + "_plantUML_" + diagramType;
	}

	private String tagToServiceName(String tag, List<CollectedMavenInfoObject> infoObjects) {
		String name = tag;

		for (CollectedMavenInfoObject info : infoObjects) {
			if (info.getTag().equals(tag)) {
				return "service: " + info.getProjectName();
			}
		}

		return "service: " + name;
	}
}
