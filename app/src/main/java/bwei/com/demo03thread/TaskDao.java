package bwei.com.demo03thread;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


public class TaskDao {

    private SQLiteDatabase db;

    public TaskDao(Context context, String dbName, int version) {
        MyDatabaseHelper databaseHelper = new MyDatabaseHelper(context, dbName, null, version);
        db = databaseHelper.getReadableDatabase();
    }

    // 更新断点
    public void savePoint(Task taskName) {
        // update point set position = ? where task = ? and thread = ?;
        ContentValues values = new ContentValues();
        values.put("position", taskName.position);
        db.update("point",values, "task = ? and thread = ?", new String[] {taskName.task, String.valueOf(taskName.thread)});
    }
    // 每次有一个线程，就准备一个断点
    public void addPoint(Task taskName) {
        // insert into point(task, thread, position) values(?, ?, ?);
        ContentValues values = new ContentValues();
        values.put("task", taskName.task);
        values.put("thread", taskName.thread);
        values.put("position", taskName.position);
        db.insert("point", null, values);
    }

    // 下载完成后，删除已下载文件的所有断点
    // delete from point where task = ?;
    public void delete(String taskName) {
        db.delete("point", "task = ?", new String[]{taskName});
    }

    // 从数据库获取断点
    public long getLastPoint(String taskName, int threadId) {
        // 没有断点就返回-1
        long lastPoint = -1;
        // select position form point where task = ? and thread = ?;
        Cursor cursor = db.query("point", new String[] {"position"}, "task = ? and thread = ? ", new String[]{taskName, String.valueOf(threadId)}, null, null, null);
        // 条件，游标能否定位到下一行。这里只有一个唯一结果用if就行
        if (cursor.moveToNext()) {
            lastPoint = cursor.getLong(cursor.getColumnIndex("position"));
        }
        // 关闭结果集
        cursor.close();
        return lastPoint;
    }
}
