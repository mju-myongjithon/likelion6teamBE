insert into group_members (group_id, user_id, joined_at)
select groups.group_id, groups.leader_user_id, groups.created_at
from groups
where not exists (
	select 1
	from group_members
	where group_members.group_id = groups.group_id
		and group_members.user_id = groups.leader_user_id
)
on conflict (group_id, user_id) do nothing;
