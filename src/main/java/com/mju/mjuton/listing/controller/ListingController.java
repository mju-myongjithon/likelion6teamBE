package com.mju.mjuton.listing.controller;

import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.listing.service.ListingFilter;
import com.mju.mjuton.listing.service.ListingItem;
import com.mju.mjuton.listing.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
@Tag(name = "통합 목록", description = "스터디와 해커톤·행사를 필터링해 조회합니다.")
public class ListingController {
	private final ListingService listingService;

	public ListingController(ListingService listingService) {
		this.listingService = listingService;
	}

	@GetMapping
	@Operation(summary = "스터디·해커톤 통합 목록 조회", description = "filter 생략 시 ALL로 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "목록 조회 성공",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							array = @ArraySchema(schema = @Schema(implementation = ListingItem.class)))),
			@ApiResponse(responseCode = "400", description = "잘못된 필터",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ErrorResponse.class)))
	})
	List<ListingItem> findAll(
			@Parameter(description = "조회 필터. 생략하면 ALL입니다.",
					schema = @Schema(allowableValues = {"ALL", "STUDY", "HACKATHON"}, defaultValue = "ALL"))
			@RequestParam(required = false) String filter) {
		return listingService.findAll(ListingFilter.from(filter));
	}
}
