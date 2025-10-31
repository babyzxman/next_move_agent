package org.gable.blendata.nextmove.client.custom.pathfilter;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

public class AndPathFilter implements PathFilter {

    private final PathFilter[] filters;
    public AndPathFilter(PathFilter... filters){
        this.filters = filters;
    }

    @Override
    public boolean accept(Path path) {
        for (PathFilter filter : filters) {
            if (!filter.accept(path)) {
                return false;
            }
        }
        return true;
    }
}
