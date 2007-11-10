package org.jvnet.maven.plugin.antrun;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exposes maven components to the Ant tasks.
 *
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id: MavenComponentBag.java 510478 2007-02-22 12:29:45Z vsiveton $
 */
final class MavenComponentBag {
    /**
     * Used for resolving artifacts
     */
    public final ArtifactResolver resolver;
    
    /**
     * Factory for creating artifact objects
     */
    public final ArtifactFactory factory;
    
    /**
     * The local repository where the artifacts are located
     */
    public final ArtifactRepository localRepository;
    
    /**
     * The remote repositories where artifacts are located
     */
    public final List remoteRepositories;
    
    /*
     * The maven project
     */
    public final MavenProject project;
    
    /*
     * TODO: document that this is
     */
    public final ArtifactMetadataSource artifactMetadataSource;
    
    /**
     * The boolean flag that indicates whether or not to verify that the
     * resolved artifact is contained in the pom.xml file.
     * Default is 'true'.
     */
    public final boolean verifyArtifact = true;

    public final MavenProjectHelper projectHelper;
    
    /**
     * Creates a wrapper and associates that with the current thread.
     *
     * @param resolver
     * @param factory
     * @param localRepository
     * @param remoteRepositories
     */
    /*package*/ MavenComponentBag(
        ArtifactResolver resolver, 
        ArtifactFactory factory,
        ArtifactRepository localRepository, 
        List remoteRepositories, 
        MavenProject project,
        MavenProjectHelper projectHelper,
        ArtifactMetadataSource artifactMetadataSource) 
    {
        this.resolver = resolver;
        this.factory = factory;
        this.localRepository = localRepository;
        this.remoteRepositories = remoteRepositories;
        this.project = project;
        this.projectHelper = projectHelper;
        this.artifactMetadataSource = artifactMetadataSource;
        INSTANCES.set(this);
    }
    
    /**
     * Return the artifact path in the local repository for an artifact defined by its <code>groupId</code>,
     * its <code>artifactId</code> and its <code>version</code>.
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @return the locale artifact path
     * @throws IOException if any
     */
    public String getArtifactAbsolutePath( String groupId, String artifactId, String version )
    throws IOException {
        Artifact artifact = factory.createArtifact( groupId, artifactId, version, "compile", "jar" );
        try {
            resolver.resolve( artifact, remoteRepositories, localRepository );
            
            return artifact.getFile().getAbsolutePath();
        } catch ( ArtifactResolutionException e ) {
            throw new IOException( "Unable to resolve artifact: " + groupId + ":" + artifactId + ":" + version );
        } catch ( ArtifactNotFoundException e ) {
            throw new IOException( "Unable to find artifact: " + groupId + ":" + artifactId + ":" + version );
        }
    }
    
    
    private static final ThreadLocal<MavenComponentBag> INSTANCES = new ThreadLocal<MavenComponentBag>();
    
    public static MavenComponentBag get() {
        return INSTANCES.get();
    }
    
    /**
     * Releases the instance tied to the thread to avoid memory leak.
     */
    public static void reset() {
        INSTANCES.set(null);
    }
    
    /**
     * This is a helper method that provides a facade for creating Maven artifacts
     * with a classifier.  It contains additional code to scan the MavenProject
     * artifacts when the groupId, version, and/or classifier values are null.
     * This method will scan the artifacts to see if there is a unique match
     * based on the artifactId.  If there is not a match, an exception is
     * thrown.
     *
     * @param groupId
     * A String containing the maven artifact's group id
     * @param artifactId
     * A String containing the maven artifact's artifactId
     * @param version
     * A String containing the maven artifact's version
     * @param type
     * A String containing the maven artifact's type
     * @param classifier
     * A String containing the maven artifact's classifier
     * @throws ArtifactResolutionException
     * Thrown if artifact cannot be resolved
     * @throws ArtifactNotFoundException
     * Thrown if artifact is resolved and verifyArtifact is 'true' and artifact
     * is not configured in the pom.xml file.
     */
    public Artifact createArtifactWithClassifier(String groupId,
            String artifactId,
            String version,
            String type,
            String classifier)
            throws IOException {
        Artifact artifact = null;
        if (artifactId == null) {
            throw new IOException("Cannot resolve artifact: artifactId is null");
        } else if (groupId == null || version == null || classifier == null) {
            artifact = resolveArtifactUsingMavenProjectArtifacts(artifactId, groupId, version, type, classifier);
            // If no matches, throw exception
            if (artifact == null) {
                throw new IOException("Cannot resolve artifact. " +" groupId: "+ groupId 
                        + " artifactId: " + artifactId + " version: " + version 
                        + " type: "+ type + " classifier: "+ classifier);
            }
        } else {
            artifact = factory.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
            if (verifyArtifact) {
                // If 'true', check to see if artifact can be resolved by looking
                // at artifacts configured in the MavenProject object via the pom.xml file
                artifact = resolveArtifactUsingMavenProjectArtifacts(artifactId, groupId, version, type, classifier);
                // If no matches, throw exception
                if (artifact == null) {
                    throw new IOException("Artifact not found in pom.xml. " +" groupId: "+ groupId 
                        + " artifactId: " + artifactId + " version: " + version 
                        + " type: "+ type + " classifier: "+ classifier);
                }
            }
        }
        
        return artifact;
    }
    
    public ArtifactResolutionResult resolveTransitively(
        String groupId,
        String artifactId,
        String version,
        String type,
        String classifier) 
        throws ArtifactResolutionException, 
               ArtifactNotFoundException,
               IOException {
        Set artifacts = new HashSet();
        Artifact artifact = createArtifactWithClassifier(
            groupId,
            artifactId,
            version,
            type,
            classifier);
        ArtifactResolutionResult result = resolver.resolveTransitively(
            artifacts,
            artifact,
            localRepository,
            remoteRepositories,
            artifactMetadataSource, 
            new ScopeArtifactFilter("runtime") );
        
        return result;
    }
    
    /*
     * This method tries to match a request against artifacts loaded by Maven and
     * exposed through the MavenProject object.
     *
     * NOTE: This method may return 'null'.
     */
    private Artifact resolveArtifactUsingMavenProjectArtifacts(String artifactId,
            String groupId,
            String version,
            String type,
            String classifier)
            throws IOException {
        Artifact artifactMatch = null;
        // Do match based on artifactId.  If value is null or if no match, throw exception
        if (artifactId == null) {
            throw new IOException("Cannot resolve artifact: artifactId is null");
        }
        Set<Artifact> artifacts = project.getArtifacts();
        for (Artifact artifact : artifacts) {
            String aid = artifact.getArtifactId();
            if (aid != null && aid.equalsIgnoreCase(artifactId)) {
                artifactMatch = artifact;
                // Match based on artifactId.  Try to match based on groupId
                String gid = artifact.getGroupId();
                if (gid != null && gid.equalsIgnoreCase(groupId)) {
                    artifactMatch = artifact;
                    // Match based on groupId.  Try to match based on version
                    String ver = artifact.getVersion();
                    if (ver != null && ver.equalsIgnoreCase(version)) {
                        artifactMatch = artifact;
                        // Match based on version.  Try to match based on type
                        String typ = artifact.getType();
                        if (typ != null && typ.equalsIgnoreCase(type)) {
                            artifactMatch = artifact;
                            // Match based on type. Try to match based on classifier
                            String cls = artifact.getClassifier();
                            if (cls != null && cls.equalsIgnoreCase(classifier)) {
                                artifactMatch = artifact;
                                // We have an exact match. Stop processing.
                                break;
                            }
                        }
                    }
                }
            }
        }
        return artifactMatch;
    }
}
