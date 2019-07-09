package com.muwire.core.content

import com.muwire.core.search.QueryEvent

abstract class Matcher {
    final Match [] matches = Collections.synchronizedList(new ArrayList<>())
    
    protected abstract boolean match(String []searchTerms);
    
    public abstract String getTerm();
    
    public void process(QueryEvent qe) {
        def terms = qe.searchEvent.searchTerms
        if (match(terms)) {
            long now = System.currentTimeMillis()
            matches << new Match(persona : qe.originator, keywords : terms, timestamp : now)
        }
    }
}
