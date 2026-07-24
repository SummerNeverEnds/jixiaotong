package com.jixiaotong.performance.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ReviewGradedEvent extends ApplicationEvent {
    
    private final Long reviewId;

    public ReviewGradedEvent(Object source, Long reviewId) {
        super(source);
        this.reviewId = reviewId;
    }
}
