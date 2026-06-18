package net.vheerden.archi.mcp.model;

import java.util.List;

import net.vheerden.archi.mcp.model.routing.CoincidentSegmentDetector;

/**
 * A connection's visual path for layout quality assessment.
 * pathPoints is the ordered list of (x,y) coordinates forming the path:
 * source center, then any bendpoints, then target center.
 * Implements CoincidentAssessable for coincident segment detection.
 */
record AssessmentConnection(String id, String sourceNodeId, String targetNodeId,
                            List<double[]> pathPoints, String labelText, int textPosition)
        implements CoincidentSegmentDetector.CoincidentAssessable {}
