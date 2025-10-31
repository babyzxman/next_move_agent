package org.gable.blendata.nextmove.client.custom.pathfilter;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

public class OrPathFilter implements PathFilter {

    private final PathFilter[] filters;
    public OrPathFilter(PathFilter... filters){
        this.filters = filters;
    }

    @Override
    public boolean accept(Path path) {
        for (PathFilter filter : filters) {
            if (filter.accept(path)) {
                return true;
            }
        }
        return false;
    }
}
