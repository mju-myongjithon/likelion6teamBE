package com.mju.mjuton.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.GroupJoinApplicationStatus;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupJoinApplicationRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.group.service.GroupMembershipService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class GroupMembershipIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired StudyGroupRepository groups;
	@Autowired GroupMemberRepository members;
	@Autowired GroupJoinApplicationRepository applications;
	@Autowired GroupMembershipService memberships;
	@Autowired JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clearGroups() {
		groups.deleteAll();
	}

	@Test
	void leaderApprovesApplicationAndOnlyMembersCanSeeMemberList() throws Exception {
		MockHttpSession leader = sessionFor("membership-leader@mju.ac.kr");
		MockHttpSession applicant = sessionFor("membership-applicant@mju.ac.kr");
		MockHttpSession outsider = sessionFor("membership-outsider@mju.ac.kr");
		long leaderId = userId(leader);
		long applicantId = userId(applicant);
		long groupId = create(leader, 3);

		mvc.perform(get("/api/groups/{groupId}/members", groupId).session(leader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].userId").value(leaderId))
				.andExpect(jsonPath("$[0].role").value("LEADER"));

		long applicationId = apply(groupId, applicant);
		mvc.perform(get("/api/groups/{groupId}/applications", groupId).session(applicant))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
		mvc.perform(post("/api/groups/{groupId}/applications/{applicationId}/approve", groupId, applicationId)
						.session(outsider))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));

		mvc.perform(post("/api/groups/{groupId}/applications/{applicationId}/approve", groupId, applicationId)
						.session(leader))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/groups/{groupId}/members", groupId).session(applicant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].role").value("LEADER"))
				.andExpect(jsonPath("$[1].userId").value(applicantId))
				.andExpect(jsonPath("$[1].role").value("MEMBER"));
		mvc.perform(get("/api/groups/{groupId}/members", groupId).session(outsider))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GROUP_MEMBER_REQUIRED"));
	}

	@Test
	void duplicateApplicationCloseRejectAndReapplyFollowStateRules() throws Exception {
		MockHttpSession leader = sessionFor("state-leader@mju.ac.kr");
		MockHttpSession applicant = sessionFor("state-applicant@mju.ac.kr");
		long groupId = create(leader, 3);
		long applicationId = apply(groupId, applicant);

		mvc.perform(post("/api/groups/{groupId}/applications", groupId).session(applicant))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
		mvc.perform(post("/api/groups/{groupId}/applications/{applicationId}/reject", groupId, applicationId)
						.session(leader))
				.andExpect(status().isNoContent());
		assertThat(apply(groupId, applicant)).isEqualTo(applicationId);

		mvc.perform(post("/api/groups/{groupId}/close", groupId).session(leader))
				.andExpect(status().isNoContent());
		mvc.perform(post("/api/groups/{groupId}/applications/{applicationId}/approve", groupId, applicationId)
						.session(leader))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GROUP_RECRUITMENT_CLOSED"));
		mvc.perform(get("/api/groups/{groupId}", groupId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));

		mvc.perform(post("/api/groups/{groupId}/reopen", groupId).session(leader))
				.andExpect(status().isNoContent());
		mvc.perform(post("/api/groups/{groupId}/applications/{applicationId}/approve", groupId, applicationId)
						.session(leader))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/api/groups/{groupId}", groupId).session(leader))
				.andExpect(status().isNoContent());
		assertThat(members.countByGroup_Id(groupId)).isZero();
		assertThat(applications.findAll()).noneMatch(application -> application.getGroupId() == groupId);
	}

	@Test
	void capacityIsEnforcedAndCannotBeReducedBelowCurrentMemberCount() throws Exception {
		MockHttpSession leader = sessionFor("capacity-leader@mju.ac.kr");
		MockHttpSession first = sessionFor("capacity-first@mju.ac.kr");
		MockHttpSession second = sessionFor("capacity-second@mju.ac.kr");
		long groupId = create(leader, 2);

		approve(groupId, apply(groupId, first), leader);
		long secondApplicationId = apply(groupId, second);
		mvc.perform(post("/api/groups/{groupId}/applications/{applicationId}/approve", groupId,
						secondApplicationId).session(leader))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GROUP_CAPACITY_FULL"));

		mvc.perform(put("/api/groups/{groupId}", groupId).session(leader).contentType(MediaType.APPLICATION_JSON)
						.content(groupRequest(1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void leaderTransfersAuthorityBeforeLeavingAndNewLeaderCanRemoveMember() throws Exception {
		MockHttpSession leader = sessionFor("transfer-leader@mju.ac.kr");
		MockHttpSession nextLeader = sessionFor("transfer-next@mju.ac.kr");
		MockHttpSession member = sessionFor("transfer-member@mju.ac.kr");
		long oldLeaderId = userId(leader);
		long nextLeaderId = userId(nextLeader);
		long memberId = userId(member);
		long groupId = create(leader, 3);
		approve(groupId, apply(groupId, nextLeader), leader);
		approve(groupId, apply(groupId, member), leader);

		mvc.perform(post("/api/groups/{groupId}/leave", groupId).session(leader))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LEADER_MUST_TRANSFER_FIRST"));
		mvc.perform(post("/api/groups/{groupId}/transfer-leader", groupId).session(leader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"newLeaderUserId\":" + nextLeaderId + "}"))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/groups/{groupId}", groupId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.leaderUserId").value(nextLeaderId));
		mvc.perform(get("/api/groups/{groupId}/members", groupId).session(nextLeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].userId").value(oldLeaderId))
				.andExpect(jsonPath("$[0].role").value("MEMBER"))
				.andExpect(jsonPath("$[1].role").value("LEADER"));

		mvc.perform(post("/api/groups/{groupId}/leave", groupId).session(leader))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/api/groups/{groupId}/members/{memberUserId}", groupId, memberId)
						.session(nextLeader))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/groups/{groupId}/members", groupId).session(member))
				.andExpect(status().isForbidden());
	}

	@Test
	void concurrentApprovalsCannotExceedCapacity() throws Exception {
		MockHttpSession leader = sessionFor("concurrent-leader@mju.ac.kr");
		MockHttpSession first = sessionFor("concurrent-first@mju.ac.kr");
		MockHttpSession second = sessionFor("concurrent-second@mju.ac.kr");
		long leaderId = userId(leader);
		long groupId = create(leader, 2);
		long firstApplicationId = apply(groupId, first);
		long secondApplicationId = apply(groupId, second);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<String> firstResult = executor.submit(
					() -> approveConcurrently(start, groupId, firstApplicationId, leaderId));
			Future<String> secondResult = executor.submit(
					() -> approveConcurrently(start, groupId, secondApplicationId, leaderId));
			start.countDown();

			assertThat(List.of(firstResult.get(), secondResult.get()))
					.containsExactlyInAnyOrder("APPROVED", "GROUP_CAPACITY_FULL");
			assertThat(members.countByGroup_Id(groupId)).isEqualTo(2);
			assertThat(applications.findAll().stream()
					.filter(application -> application.getGroupId() == groupId)
					.map(application -> application.getStatus())
					.toList())
					.containsExactlyInAnyOrder(
							GroupJoinApplicationStatus.APPROVED, GroupJoinApplicationStatus.PENDING);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void existingGroupWithoutMemberRowStillTreatsCreatorAsLeader() throws Exception {
		MockHttpSession leader = sessionFor("legacy-leader@mju.ac.kr");
		MockHttpSession applicant = sessionFor("legacy-applicant@mju.ac.kr");
		User leaderUser = users.findById(userId(leader)).orElseThrow();
		StudyGroup group = new StudyGroup(leaderUser, "기존 모임", "기존 데이터", 2, "매주", "서울");
		group.replaceRoles(List.of());
		long groupId = groups.saveAndFlush(group).getId();

		mvc.perform(get("/api/groups/{groupId}/members", groupId).session(leader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].userId").value(userId(leader)))
				.andExpect(jsonPath("$[0].role").value("LEADER"));
		approve(groupId, apply(groupId, applicant), leader);
		assertThat(members.countByGroup_Id(groupId)).isEqualTo(1);

		mvc.perform(post("/api/groups/{groupId}/transfer-leader", groupId).session(leader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"newLeaderUserId\":" + userId(applicant) + "}"))
				.andExpect(status().isNoContent());
		assertThat(members.countByGroup_Id(groupId)).isEqualTo(2);
	}

	@Test
	void applicantCanReadOnlyOwnApplicationIncludingDecidedStatus() throws Exception {
		MockHttpSession leader = sessionFor("my-application-leader@mju.ac.kr");
		MockHttpSession applicant = sessionFor("my-application-applicant@mju.ac.kr");
		MockHttpSession other = sessionFor("my-application-other@mju.ac.kr");
		long groupId = create(leader, 3);
		long applicationId = apply(groupId, applicant);

		mvc.perform(get("/api/groups/{groupId}/applications/me", groupId).session(applicant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicationId").value(applicationId))
				.andExpect(jsonPath("$.groupId").value(groupId))
				.andExpect(jsonPath("$.applicantUserId").value(userId(applicant)))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.requestedAt").isNotEmpty())
				.andExpect(jsonPath("$.decidedAt").isEmpty());
		mvc.perform(get("/api/groups/{groupId}/applications/me", groupId).session(other))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_APPLICATION_NOT_FOUND"));

		approve(groupId, applicationId, leader);
		mvc.perform(get("/api/groups/{groupId}/applications/me", groupId).session(applicant))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.decidedAt").isNotEmpty());

		long rejectedGroupId = create(leader, 3);
		long rejectedApplicationId = apply(rejectedGroupId, other);
		mvc.perform(post("/api/groups/{groupId}/applications/{applicationId}/reject",
						rejectedGroupId, rejectedApplicationId).session(leader))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/groups/{groupId}/applications/me", rejectedGroupId).session(other))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.decidedAt").isNotEmpty());
	}

	@Test
	void myApplicationDistinguishesMissingGroupAndApplicationAndRequiresLogin() throws Exception {
		MockHttpSession leader = sessionFor("my-application-errors-leader@mju.ac.kr");
		MockHttpSession applicant = sessionFor("my-application-errors-applicant@mju.ac.kr");
		MockHttpSession invalidUser = new MockHttpSession();
		invalidUser.setAttribute(AuthController.SESSION_USER_ID, Long.MAX_VALUE);
		long groupId = create(leader, 2);

		mvc.perform(get("/api/groups/{groupId}/applications/me", groupId).session(applicant))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_APPLICATION_NOT_FOUND"));
		mvc.perform(get("/api/groups/{groupId}/applications/me", Long.MAX_VALUE).session(applicant))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
		mvc.perform(get("/api/groups/{groupId}/applications/me", groupId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(get("/api/groups/{groupId}/applications/me", groupId).session(invalidUser))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void myGroupsContainOnlyLedOrApprovedGroupsWithoutDuplicatesAndUseStableOrdering() throws Exception {
		MockHttpSession leader = sessionFor("my-groups-leader@mju.ac.kr");
		MockHttpSession member = sessionFor("my-groups-member@mju.ac.kr");
		MockHttpSession pending = sessionFor("my-groups-pending@mju.ac.kr");
		MockHttpSession rejected = sessionFor("my-groups-rejected@mju.ac.kr");
		MockHttpSession otherLeader = sessionFor("my-groups-other-leader@mju.ac.kr");
		MockHttpSession ledGroupMember = sessionFor("my-groups-led-member@mju.ac.kr");
		long firstLedGroupId = create(leader, 5);
		long secondLedGroupId = create(leader, 5);
		long approvedGroupId = create(otherLeader, 5);
		long excludedGroupId = create(otherLeader, 5);
		approve(firstLedGroupId, apply(firstLedGroupId, ledGroupMember), leader);
		approve(approvedGroupId, apply(approvedGroupId, member), otherLeader);
		apply(excludedGroupId, pending);
		long rejectedApplicationId = apply(excludedGroupId, rejected);
		mvc.perform(post("/api/groups/{groupId}/applications/{applicationId}/reject",
						excludedGroupId, rejectedApplicationId).session(otherLeader))
				.andExpect(status().isNoContent());
		jdbcTemplate.update("update groups set created_at = ? where group_id in (?, ?)",
				java.sql.Timestamp.from(java.time.Instant.parse("2026-07-17T00:00:00Z")),
				firstLedGroupId, secondLedGroupId);

		mvc.perform(get("/api/groups/me").session(leader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].groupId").value(secondLedGroupId))
				.andExpect(jsonPath("$[1].groupId").value(firstLedGroupId))
				.andExpect(jsonPath("$[0].role").value("LEADER"))
				.andExpect(jsonPath("$[0].title").value("권한 모델 스터디"))
				.andExpect(jsonPath("$[0].category").value("STUDY"))
				.andExpect(jsonPath("$[0].status").value("RECRUITING"))
				.andExpect(jsonPath("$[0].location").value("강남"))
				.andExpect(jsonPath("$[0].meetingRule").value("매주 토요일"))
				.andExpect(jsonPath("$[0].maxMemberCount").value(5))
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty());
		mvc.perform(get("/api/groups/me").session(member))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].groupId").value(approvedGroupId))
				.andExpect(jsonPath("$[0].role").value("MEMBER"));
		mvc.perform(get("/api/groups/me").session(pending))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
		mvc.perform(get("/api/groups/me").session(rejected))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void myGroupsCalculateRoleAfterTransferAndSupportLegacyLeaderWithoutMemberRow() throws Exception {
		MockHttpSession leader = sessionFor("my-groups-transfer-leader@mju.ac.kr");
		MockHttpSession nextLeader = sessionFor("my-groups-transfer-next@mju.ac.kr");
		long groupId = create(leader, 3);
		approve(groupId, apply(groupId, nextLeader), leader);
		mvc.perform(post("/api/groups/{groupId}/transfer-leader", groupId).session(leader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"newLeaderUserId\":" + userId(nextLeader) + "}"))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/groups/me").session(leader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].groupId").value(groupId))
				.andExpect(jsonPath("$[0].role").value("MEMBER"));
		mvc.perform(get("/api/groups/me").session(nextLeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].groupId").value(groupId))
				.andExpect(jsonPath("$[0].role").value("LEADER"));

		MockHttpSession legacyLeader = sessionFor("my-groups-legacy-leader@mju.ac.kr");
		User legacyLeaderUser = users.findById(userId(legacyLeader)).orElseThrow();
		StudyGroup legacyGroup = new StudyGroup(legacyLeaderUser, "레거시 모임", "기존 데이터",
				2, "매주", "서울");
		legacyGroup.replaceRoles(List.of());
		long legacyGroupId = groups.saveAndFlush(legacyGroup).getId();
		assertThat(members.existsByGroup_IdAndUser_Id(legacyGroupId, userId(legacyLeader))).isFalse();

		mvc.perform(get("/api/groups/me").session(legacyLeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].groupId").value(legacyGroupId))
				.andExpect(jsonPath("$[0].role").value("LEADER"));
	}

	@Test
	void myGroupsReturnsEmptyArrayAndRequiresLogin() throws Exception {
		MockHttpSession user = sessionFor("my-groups-empty@mju.ac.kr");
		MockHttpSession invalidUser = new MockHttpSession();
		invalidUser.setAttribute(AuthController.SESSION_USER_ID, Long.MAX_VALUE);

		mvc.perform(get("/api/groups/me").session(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
		mvc.perform(get("/api/groups/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(get("/api/groups/me").session(invalidUser))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	private String approveConcurrently(CountDownLatch start, long groupId, long applicationId, long leaderId)
			throws InterruptedException {
		start.await();
		try {
			memberships.approve(groupId, applicationId, leaderId);
			return "APPROVED";
		} catch (ApiException exception) {
			return exception.getCode();
		}
	}

	private long create(MockHttpSession leader, int capacity) throws Exception {
		MvcResult result = mvc.perform(post("/api/groups").session(leader).contentType(MediaType.APPLICATION_JSON)
						.content(groupRequest(capacity)))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.groupId")).longValue();
	}

	private long apply(long groupId, MockHttpSession applicant) throws Exception {
		MvcResult result = mvc.perform(post("/api/groups/{groupId}/applications", groupId).session(applicant))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.applicationId")).longValue();
	}

	private void approve(long groupId, long applicationId, MockHttpSession leader) throws Exception {
		mvc.perform(post("/api/groups/{groupId}/applications/{applicationId}/approve", groupId, applicationId)
						.session(leader))
				.andExpect(status().isNoContent());
	}

	private MockHttpSession sessionFor(String email) {
		User user = users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private long userId(MockHttpSession session) {
		return (Long) session.getAttribute(AuthController.SESSION_USER_ID);
	}

	private String groupRequest(int capacity) {
		return "{\"title\":\"권한 모델 스터디\",\"description\":\"함께 공부합니다.\","
				+ "\"maxMemberCount\":" + capacity + ",\"meetingRule\":\"매주 토요일\","
				+ "\"location\":\"강남\",\"recruitingRoles\":[]}";
	}
}
