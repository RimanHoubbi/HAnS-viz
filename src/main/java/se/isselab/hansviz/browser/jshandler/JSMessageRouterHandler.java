package se.isselab.hansviz.browser.jshandler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import se.isselab.HAnS.featureExtension.FeatureService;
import se.isselab.HAnS.featureModel.psi.FeatureModelFeature;
import se.isselab.HAnS.featureModel.psi.impl.FeatureModelFeatureImpl;
import se.isselab.HAnS.featureModel.FeatureModelUtil;
import com.intellij.openapi.application.ReadAction;

import se.isselab.hansviz.JSONHandler.JSONHandler;

import com.intellij.openapi.project.Project;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.util.Arrays;
import java.util.List;

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

public class JSMessageRouterHandler extends CefMessageRouterHandlerAdapter {
    private final Project project;
    public JSMessageRouterHandler(Project project){
        this.project = project;
    }

    //private final FeatureService service = ProjectManager.getInstance().getOpenProjects()[0].getService(FeatureService.class);

    // &begin[Request]

    /**
     * Request on javascript side (that are called with window.java or window.javacancel are handeled here
     * @param browser CefBrowser
     * @param frame CefFrame
     * @param queryId long
     * @param request String: This request will be handled
     * @param persistent boolean
     * @param callback CefQueryCallback: function that is called after success
     * @return boolean
     */
    @Override
    public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback) {
        String[] requestTokens = request.split(",");
        switch (requestTokens[0]) {
            // Add all queries that need to be handled
            case "buttonClicked" -> {
                return true;
            }
            case "refresh" -> {
                // return JSON through parameter of success function
                callback.success("JSON");
                return true;
            }
            case "tangling" -> {

                // creates new JSONHandler for Tangling Graph
                new JSONHandler(project, JSONHandler.JSONType.Tangling, callback);
                return true;
            }
            case "tree" -> {
                new JSONHandler(project, JSONHandler.JSONType.Tree, callback);
                return true;
            }
            case "highlightFeature" -> {
                if(requestTokens.length < 2)
                    return false;

                FeatureService featureService = project.getService(FeatureService.class);
                if(featureService == null)
                    return false;

                featureService.highlightFeatureInFeatureModel(requestTokens[1]);
                callback.success("");

                return true;
            }
            case "openPath" -> {
                if(requestTokens.length < 2)
                    return false;
                FeatureService featureService = project.getService(FeatureService.class);
                if(featureService == null)
                    return false;
                if(requestTokens.length>=4){
                    featureService.openFileInProject(requestTokens[1], Integer.parseInt(requestTokens[2]), Integer.parseInt(requestTokens[3]));
                }
                else featureService.openFileInProject(requestTokens[1]);
                callback.success("");
                return true;
            }
            case "addFeature" -> {
                FeatureModelFeature parentFeature = getFeatureFromLPQ(requestTokens[1]);
                if (parentFeature == null) { return false; }
                String result = parentFeature.addToFeatureModel(requestTokens[2]);
                if (result.equals(requestTokens[2].trim())) {
                    callback.success("JSON");
                    return true;
                } else {
                    callback.failure(-1, "Feature name is invalid");
                    return false;
                }
            }
            case "deleteFeature" -> {
                FeatureModelFeature childFeature = getFeatureFromLPQ(requestTokens[1]);
                if (childFeature == null) { return false; }
                childFeature.deleteFromFeatureModel();
                callback.success("JSON");
                return true;
            }
            case "moveFeature" -> {
                FeatureModelFeature childFeature = getFeatureFromLPQ(requestTokens[1]);
                FeatureModelFeature newParentFeature = getFeatureFromLPQ(requestTokens[2]);

                if (childFeature == null) { return false; }
                if (newParentFeature == null) { return false; }

                // check that parentFeature isn't child of childFeature
                if (checkFeatureChildOfParent(childFeature, newParentFeature)) {
                    return false;
                }
                // check that childFeature isn't already a direct child of parentFeature
                // otherwise the operation is useless
                for(PsiElement child : newParentFeature.getChildren()) {
                    if (child.equals(childFeature)) {
                        return true;
                    }
                }

                childFeature = childFeature.deleteFromFeatureModel(); // delete child from feature model tree

                // refresh to get accurate offset of parentFeature
                final Project projectInstance = ReadAction.compute(newParentFeature::getProject);
                WriteCommandAction.runWriteCommandAction(projectInstance, () -> {
                    PsiDocumentManager.getInstance(projectInstance).commitAllDocuments();
                });

                newParentFeature.addWithChildren(childFeature);
                callback.success("JSON");
                return true;
            }
            case "renameFeature" -> {
                FeatureModelFeature childFeature = getFeatureFromLPQ(requestTokens[1]);
                String newFeatureName = requestTokens[2];
                if (childFeature == null) { return false; }

                if (childFeature.renameInFeatureModel(newFeatureName)){
                    callback.success("JSON");
                    return true;
                };
                return false;
            }
        }
        return false;
    }
    // &end[Request]

    private FeatureModelFeature getFeatureFromLPQ(String lpq) {
        List<FeatureModelFeature> listOfFeatures = ReadAction.compute(() -> FeatureModelUtil.findLPQ(project, lpq));
        if (listOfFeatures.isEmpty()) { return null; }
        FeatureModelFeature feature = (FeatureModelFeature) listOfFeatures.get(0);
        return feature;
    }

    private static boolean checkFeatureChildOfParent(FeatureModelFeature parentFeature, FeatureModelFeature childFeature) {
        if (parentFeature.equals(childFeature)) {
            return true; // Found the child feature at this level
        }

        // Check children recursively
        PsiElement[] children = parentFeature.getChildren();
        for (PsiElement child : children) {
            if (checkFeatureChildOfParent(((FeatureModelFeature) child), childFeature)) {
                return true; // Child feature found in one of the children
            }
        }

        return false; // Child feature not found in this subtree
    }
}
