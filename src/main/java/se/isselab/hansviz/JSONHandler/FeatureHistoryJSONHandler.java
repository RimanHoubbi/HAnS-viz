package se.isselab.hansviz.JSONHandler;

import com.intellij.openapi.project.Project;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.cef.callback.CefQueryCallback;
import se.isselab.HAnS.featureHistoryView.FeatureCommitMapper;

import java.util.List;
import java.util.Map;

public class FeatureHistoryJSONHandler {
    private final CefQueryCallback callback;
    private final JSONType jsonType;
    private final Project project;
    public enum JSONType {FEATURE_HISTORY}
    public FeatureHistoryJSONHandler(Project project, CefQueryCallback callback, FeatureHistoryJSONHandler.JSONType type) {
        this.project = project;
        this.callback = callback;
        this.jsonType = type;
        handleFeatureHistoryRequest();
    }

    private void handleFeatureHistoryRequest() {
        // Call the mapFeaturesAndCommits method with a Runnable that handles the callback
        FeatureCommitMapper featureCommitMapper = new FeatureCommitMapper();
        featureCommitMapper.mapFeaturesAndCommits(project, () -> {
            // Once the data is ready, send the JSON response
            callback.success(getFeatureHistoryJSON(featureCommitMapper));
        });
    }

    /**
     * Method to generate JSON for Feature History.
     */
    private String getFeatureHistoryJSON(FeatureCommitMapper featureCommitMapper) {
        // Get the data
        JSONArray featuresArray = new JSONArray();
        JSONArray commitsArray = new JSONArray();
        JSONArray codeAnnotationsArray = new JSONArray();
        JSONArray fileMappingsArray = new JSONArray();
        JSONArray folderMappingsArray = new JSONArray();
        JSONArray deletedFeaturesArray = new JSONArray();

        // Add feature names
        featuresArray.addAll(featureCommitMapper.getFeatureNames());

        // Add commit times
        commitsArray.addAll(featureCommitMapper.getCommitTimes());

        // Process series data
        List<Map<String, Object>> seriesDataList = featureCommitMapper.getSeriesData();
        for (Map<String, Object> dataPoint : seriesDataList) {
            String type = (String) dataPoint.get("type");
            JSONObject point = new JSONObject();

            // Common fields
            int featureIndex = (int) dataPoint.get("featureIndex");
            int commitIndex = (int) dataPoint.get("commitIndex");
            String commitHash = (String) dataPoint.get("commitHash");
            point.put("featureIndex", featureIndex);
            point.put("commitIndex", commitIndex);
            point.put("commitHash", commitHash);

            switch (type) {
                case "codeAnnotation":
                    String commitMessage = (String) dataPoint.get("commitMessage");
                    String commitAuthor = (String) dataPoint.get("commitAuthor");
                    point.put("commitMessage", commitMessage);
                    point.put("commitAuthor", commitAuthor);
                    codeAnnotationsArray.add(point);
                    break;
                case "fileMapping":
                    String commitTimeFile = (String) dataPoint.get("commitTime");
                    String entityNameFile = (String) dataPoint.get("entityName");
                    point.put("commitTime", commitTimeFile);
                    point.put("entityName", entityNameFile);
                    fileMappingsArray.add(point);
                    break;
                case "folderMapping":
                    String commitTimeFolder = (String) dataPoint.get("commitTime");
                    String entityNameFolder = (String) dataPoint.get("entityName");
                    point.put("commitTime", commitTimeFolder);
                    point.put("entityName", entityNameFolder);
                    folderMappingsArray.add(point);
                    break;
                default:
                    // Handle unknown type if necessary
                    break;
            }
        }

        // Process deleted features as before
        for (String deletedFeature : featureCommitMapper.getDeletedFeatureNames()) {
            JSONObject deletedFeatureObject = new JSONObject();
            deletedFeatureObject.put("featureName", deletedFeature);
            deletedFeatureObject.put("lastCommitTime", featureCommitMapper.getDeletedFeatureCommits().get(deletedFeature));
            deletedFeatureObject.put("commitHash", featureCommitMapper.getDeletedFeatureCommitHashes().get(deletedFeature));
            deletedFeaturesArray.add(deletedFeatureObject);
        }

        // Create the JSON object
        JSONObject json = new JSONObject();
        json.put("type", "featureHistory");
        json.put("features", featuresArray);
        json.put("commits", commitsArray);
        json.put("codeAnnotations", codeAnnotationsArray);
        json.put("fileMappings", fileMappingsArray);
        json.put("folderMappings", folderMappingsArray);
        json.put("deletedFeatures", deletedFeaturesArray);

        // Debug output
        System.out.println("Feature History JSON: " + json);

        return json.toJSONString();
    }
}

