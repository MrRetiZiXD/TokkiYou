package com.ss.android.ugc.aweme.feed.model.live;

/**
 * What a live room in the feed carries.
 *
 * Nothing here is read: the mod only asks whether a post has one. It exists
 * because a method is found by its name and its return type together, and
 * `getRoomFeedCellStruct()` returning `Object` is a different method from the
 * one the apk has -- which the phone answered with NoSuchMethodError on every
 * post that was not a live room.
 */
public class RoomFeedCellStruct {
}
