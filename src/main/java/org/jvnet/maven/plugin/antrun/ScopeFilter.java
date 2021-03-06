package org.jvnet.maven.plugin.antrun;


import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Filter out a {@link DependencyGraph} by only traversing the given scope.
 *
 * @author Kohsuke Kawaguchi
 * @author Paul Sterk
 */
public final class ScopeFilter extends GraphFilter implements GraphVisitor {
    private final Set<String> scopes = new HashSet<String>();

    public ScopeFilter(Collection<String> scopes) {
        this.scopes.addAll(scopes);
    }

    public ScopeFilter(String... scopes) {
        this(Arrays.asList(scopes));
    }

    // needed for Ant
    public ScopeFilter() {
    }

    public void setLevel(String level) {
        StringTokenizer tokens = new StringTokenizer(level,", ");
        while(tokens.hasMoreTokens())
            scopes.add(tokens.nextToken().trim());
    }

    public DependencyGraph process() {
        // Create a subgraph of the dependencyGraph by using this class as a 
        // GraphVisitor.
        return evaluateChild().createSubGraph(this);
    }    
    
    public boolean visit(DependencyGraph.Edge edge) {
        return scopes.contains(edge.scope);
    }

    public boolean visit(DependencyGraph.Node node) {
        return true;
    }
}
