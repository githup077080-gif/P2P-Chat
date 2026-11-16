def test_bot_crud_activation_sandbox_and_isolation(client, auth_user, peer_user):
    create_res = client.post(
        "/api/bots",
        json={
            "name": "Support Helper",
            "handle": "support_helper",
            "description": "Answers support questions",
            "system_prompt": "Always reply concisely and politely.",
            "welcome_message": "Welcome to Support Helper!",
            "commands": [
                {"command": "/status", "response": "All services operational.", "description": "Check status"}
            ],
            "ai_powered": True,
            "activate_now": False,
        },
        headers=auth_user["headers"],
    )
    assert create_res.status_code == 201, create_res.get_json()
    bot = create_res.get_json()["data"]
    assert bot["handle"] == "support_helper_bot"
    assert bot["is_active"] is False
    assert bot["bot_token"].startswith("p2pbot_")
    bot_id = bot["id"]

    list_res = client.get("/api/bots", headers=auth_user["headers"])
    assert list_res.status_code == 200
    assert len(list_res.get_json()["data"]) == 1

    # Peer cannot see or mutate owner's bot
    peer_list = client.get("/api/bots", headers=peer_user["headers"])
    assert peer_list.status_code == 200
    assert peer_list.get_json()["data"] == []

    peer_patch = client.patch(
        f"/api/bots/{bot_id}",
        json={"name": "Hijacked"},
        headers=peer_user["headers"],
    )
    assert peer_patch.status_code == 403

    # Activate bot on server
    act_res = client.post(f"/api/bots/{bot_id}/activate", headers=auth_user["headers"])
    assert act_res.status_code == 200
    assert act_res.get_json()["data"]["is_active"] is True
    assert act_res.get_json()["data"]["activated_at"] is not None

    # Test slash command and webhook
    test_res = client.post(
        f"/api/bots/{bot_id}/test",
        json={"message": "/status"},
        headers=auth_user["headers"],
    )
    assert test_res.status_code == 200
    assert test_res.get_json()["data"]["reply"] == "All services operational."
    assert test_res.get_json()["data"]["matched_command"] == "/status"

    webhook_res = client.post(
        f"/api/bots/webhook/{bot['handle']}",
        json={"message": "/start"},
        headers={"X-Bot-Token": bot["bot_token"]},
    )
    assert webhook_res.status_code == 200
    assert webhook_res.get_json()["data"]["reply"] == "Welcome to Support Helper!"

    # Deactivate pauses webhook
    deact_res = client.post(f"/api/bots/{bot_id}/deactivate", headers=auth_user["headers"])
    assert deact_res.status_code == 200
    assert deact_res.get_json()["data"]["is_active"] is False

    paused_hook = client.post(
        f"/api/bots/webhook/{bot['handle']}",
        json={"message": "/status"},
        headers={"X-Bot-Token": bot["bot_token"]},
    )
    assert paused_hook.status_code == 409

    # Delete bot
    del_res = client.delete(f"/api/bots/{bot_id}", headers=auth_user["headers"])
    assert del_res.status_code == 200
    assert del_res.get_json()["data"]["deleted_id"] == bot_id
