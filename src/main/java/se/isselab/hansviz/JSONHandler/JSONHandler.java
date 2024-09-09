/*
Copyright 2024 David Stechow & Philipp Kusmierz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package se.isselab.hansviz.JSONHandler;

import com.intellij.openapi.project.Project;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.cef.callback.CefQueryCallback;

import org.jetbrains.annotations.NotNull;
import se.isselab.HAnS.featureLocation.FeatureFileMapping;
import se.isselab.HAnS.featureLocation.FeatureLocation;
import se.isselab.HAnS.featureModel.psi.FeatureModelFeature;
import se.isselab.HAnS.metrics.ProjectMetrics;
import se.isselab.HAnS.pluginExtensions.ProjectMetricsService;
import se.isselab.HAnS.pluginExtensions.backgroundTasks.MetricsCallback;
import se.isselab.hansviz.JSONHandler.pathFormatter.PathFormatter;


import java.util.*;


public class JSONHandler implements MetricsCallback {
    private final CefQueryCallback callback;
    private final JSONType jsonType;
    private final Project project;

    private final ProjectMetricsService featureService;

    public enum JSONType {DEFAULT, TREE, TREEMAP, TANGLING}

    @Override
    public void onComplete(ProjectMetrics featureMetrics) {
        JSONObject dataJSON = new JSONObject();
        JSONArray nodesJSON = new JSONArray();
        JSONArray linksJSON = new JSONArray();
        Map<String, FeatureFileMapping> featureFileMappings = featureMetrics.getFeatureFileMappings();
        Map<FeatureModelFeature, HashSet<FeatureModelFeature>> tanglingMap = featureMetrics.getTanglingMap();

        HashMap<FeatureModelFeature, Integer> featureToId = new HashMap<>();
        int counter = 0;
        Optional<List<FeatureModelFeature>> topLevelFeatures = getFeatureModelFeatures();

        if(topLevelFeatures.isPresent()){
            for(var feature : topLevelFeatures.get()){
                JSONObject featureObj = featureToJSON(feature, featureFileMappings, tanglingMap);
                nodesJSON.add(featureObj);
                featureToId.put(feature, counter);
                counter++;
            }
        }



        for(var featureToTangledFeatures : tanglingMap.entrySet()){
            for(var tangledFeature : featureToTangledFeatures.getValue()){
                //add link if id of feature is less than the id of the tangled one
                if(!featureToId.containsKey(featureToTangledFeatures.getKey()) || !featureToId.containsKey(tangledFeature))
                    continue;
                if(featureToId.get(featureToTangledFeatures.getKey()) < featureToId.get(tangledFeature))
                {
                    JSONObject obj = new JSONObject();
                    obj.put("source", featureToTangledFeatures.getKey().getLPQText());
                    obj.put("target", tangledFeature.getLPQText());
                    linksJSON.add(obj);
                }
            }
        }
        dataJSON.put("features", nodesJSON);
        dataJSON.put("tanglingLinks", linksJSON);
        callback.success(dataJSON.toJSONString());
    }

    private @NotNull Optional<List<FeatureModelFeature>> getFeatureModelFeatures() {

        return switch (jsonType) {
            case DEFAULT, TREE, TREEMAP -> Optional.ofNullable(featureService.getRootFeatures());
            case TANGLING -> Optional.ofNullable(featureService.getFeatures());
        };
    }


    public JSONHandler(Project project, JSONType type, CefQueryCallback callback) {
        this.project = project;
        this.jsonType = type;
        this.callback = callback;
        featureService = project.getService(ProjectMetricsService.class);
        featureService.getProjectMetricsBackground(this);
    }

    /**
     * Helperfunction to recursively create JSONObjects of features
     * Recursion takes place within the child property of the feature
     *
     * @param feature feature which should be converted to JSON
     * @return JSONObject of given feature
     */
    private JSONObject featureToJSON(FeatureModelFeature feature, Map<String, FeatureFileMapping> featureFileMappings, Map<FeatureModelFeature, HashSet<FeatureModelFeature>> tanglingMap){
        JSONObject obj = new JSONObject();
        obj.put("id", feature.getLPQText());
        obj.put("name", feature.getFeatureName());
        var tangledFeatureMap = featureService.getTanglingMapOfFeature((HashMap<FeatureModelFeature, HashSet<FeatureModelFeature>>) tanglingMap, feature);
        int tanglingDegree = tangledFeatureMap != null ? tangledFeatureMap.size() : 0;
        FeatureFileMapping featureFileMapping = featureService.getFeatureFileMappingOfFeature((HashMap<String, FeatureFileMapping>) featureFileMappings, feature);
        List<FeatureModelFeature> childFeatureList = featureService.getChildFeatures(feature);

        //recursively get all child features
        JSONArray childArr = new JSONArray();
        for(var child : childFeatureList){
            childArr.add(featureToJSON(child, featureFileMappings, tanglingMap));
        }

        obj.put("children", childArr);
        obj.put("tanglingDegree", tanglingDegree);
        obj.put("scatteringDegree", featureService.getFeatureScattering(featureFileMapping));

        obj.put("lines", featureService.getTotalFeatureLineCount(featureFileMapping));
        obj.put("totalLines", getTotalLineCountWithChilds(feature, (HashMap<String, FeatureFileMapping>) featureFileMappings));

        //put locations and their line count into array
        JSONArray locations = new JSONArray();
        var featureLocations = featureService.getFeatureLocations(featureFileMapping);
        for(FeatureLocation featureLocation : featureLocations){
            JSONArray blocks = new JSONArray();
            for(var block : featureService.getListOfFeatureLocationBlock(featureLocation)){
                JSONObject blockObj = new JSONObject();
                blockObj.put("start", block.getStartLine());
                blockObj.put("end", block.getEndLine());
                blockObj.put("type", featureLocation.getAnnotationType().toString());
                blocks.add(blockObj);
            }
            //get the linecount of a feature for each file and add it
            JSONObject locationObj = new JSONObject();
            if(featureFileMappings.containsKey(feature.getLPQText())){
                locationObj.put("lines", featureService.getFeatureLineCountInFile(featureFileMapping, featureLocation));
            }
            else{
                locationObj.put("lines", 0);
            }
            locationObj.put("blocks", blocks);
            locationObj.put("path", PathFormatter.shortenPathToSource(project,featureLocation.getMappedPath()));
            locationObj.put("fileName", PathFormatter.shortenPathToFile(featureLocation.getMappedPath()));
            locations.add(locationObj);
        }
        obj.put("locations", locations);
        return obj;
    }

    private JSONArray getChildFeaturesAsJson(FeatureModelFeature parentFeature, HashMap<String, FeatureFileMapping> fileMapping) {
        JSONArray children = new JSONArray();
        var childFeatureList = featureService.getChildFeatures(parentFeature);

        //iterate over each child and recursively get its childs
        for(var child : childFeatureList){
            //get linecount of feature via mapping

            JSONObject childJson = new JSONObject();
            childJson.put("name", child.getLPQText());
            childJson.put("value", getTotalLineCountWithChilds(child,fileMapping));
            childJson.put("children", getChildFeaturesAsJson(child, fileMapping));
            children.add(childJson);
        }
        return children;
    }


    private int getTotalLineCountWithChilds(FeatureModelFeature parent, HashMap<String, FeatureFileMapping> fileMapping){
        int total = 0;
        FeatureFileMapping parentFileMapping = featureService.getFeatureFileMappingOfFeature(fileMapping, parent);
        for(var child : featureService.getChildFeatures(parent)){
            total += getTotalLineCountWithChilds(child, fileMapping);
        }
        if(fileMapping.containsKey(parent.getLPQText()))
            total += featureService.getTotalFeatureLineCount(parentFileMapping);
        return total;
    }
}
