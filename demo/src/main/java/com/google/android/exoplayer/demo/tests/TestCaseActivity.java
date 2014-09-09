package com.google.android.exoplayer.demo.tests;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.exoplayer.demo.R;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class TestCaseActivity extends Activity {
  ProgressBar progressBar;
  TextView textView;

  public static final String TEST_CASE_CLASS = "TEST_CASE_CLASS";
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_test_case);
    Intent intent = getIntent();
    String testCaseClassName = intent.getStringExtra(TEST_CASE_CLASS);
    progressBar = (ProgressBar)findViewById(R.id.progressBar);
    textView = (TextView)findViewById(R.id.textView);

    TestCase testCase = null;
    Exception exception = null;
    try {
      Class testCaseClass = Class.forName(testCaseClassName);
      Class[] types = {};
      Constructor constructor = testCaseClass.getConstructor(types);

      Object[] parameters = {};
      testCase = (TestCase)constructor.newInstance(parameters);
    } catch (Exception e) {
      exception = e;
    }

    if (testCase == null) {
      exception.printStackTrace();
      textView.setText("cannot instantiate class " + testCaseClassName);
      return;
    }

    TestCaseTask task = new TestCaseTask();
    task.execute(testCase);
  }

  private class TestCaseTask extends AsyncTask<TestCase, Void, Void> {
    Exception exception;
    protected Void doInBackground(TestCase... testCases) {
      try {
        testCases[0].run();
      } catch (Exception e) {
        exception = e;
      }
      return null;
    }

    protected void onPostExecute(Void result) {
      if (exception != null) {
        exception.printStackTrace();
      }
      progressBar.setVisibility(View.INVISIBLE);
      textView.setText("Test finished");
    }
  }
}
