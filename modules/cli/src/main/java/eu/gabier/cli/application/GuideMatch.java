package eu.gabier.cli.application;

import eu.gabier.cli.domain.GuideSection;

/**
 * A section of the guide that answers a question.
 *
 * @param rank the place of the section among the answers, from 1, the best first
 * @param section the section
 * @param confidence how sure the ranking is that the section answers the question
 */
public record GuideMatch(int rank, GuideSection section, double confidence) {}
