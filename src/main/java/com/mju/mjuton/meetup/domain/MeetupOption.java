package com.mju.mjuton.meetup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "meetup_options")
public class MeetupOption {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "option_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meetup_id", nullable = false)
	private Meetup meetup;
	@Column(nullable = false)
	private int rankOrder;
	@Column(nullable = false, length = 100)
	private String placeName;
	private Double latitude;
	private Double longitude;
	@Column(length = 255)
	private String address;
	@Column(length = 50)
	private String phone;
	@Column(length = 500)
	private String reason;

	protected MeetupOption() {}

	public MeetupOption(Meetup meetup, int rankOrder, String placeName, Double latitude, Double longitude,
			String address, String phone, String reason) {
		this.meetup = meetup;
		this.rankOrder = rankOrder;
		this.placeName = placeName;
		this.latitude = latitude;
		this.longitude = longitude;
		this.address = address;
		this.phone = phone;
		this.reason = reason;
	}

	public Long getId() { return id; }
	public Long getMeetupId() { return meetup.getId(); }
	public int getRankOrder() { return rankOrder; }
	public String getPlaceName() { return placeName; }
	public Double getLatitude() { return latitude; }
	public Double getLongitude() { return longitude; }
	public String getAddress() { return address; }
	public String getPhone() { return phone; }
	public String getReason() { return reason; }
}
